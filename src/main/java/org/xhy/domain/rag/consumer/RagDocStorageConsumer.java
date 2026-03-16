package org.xhy.domain.rag.consumer;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.xhy.domain.rag.message.RagDocSyncStorageMessage;
import org.xhy.domain.rag.model.DocumentUnitEntity;
import org.xhy.domain.rag.model.FileDetailEntity;
import org.xhy.domain.rag.repository.DocumentUnitRepository;
import org.xhy.domain.rag.service.EmbeddingDomainService;
import org.xhy.domain.rag.service.FileDetailDomainService;
import org.xhy.infrastructure.mq.core.MessageEnvelope;
import org.xhy.infrastructure.mq.events.RagDocSyncStorageEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.xhy.infrastructure.mq.core.MessageHeaders.TRACE_ID;

/** @author shilong.zang
 * @date 20:51 <br/>
 */
@RabbitListener(
        concurrency = "${rag.embedding.listener.concurrency:10-20}",
        bindings = @QueueBinding(
                value = @Queue(RagDocSyncStorageEvent.QUEUE_NAME),
                exchange = @Exchange(value = RagDocSyncStorageEvent.EXCHANGE_NAME, type = ExchangeTypes.TOPIC),
                key = RagDocSyncStorageEvent.ROUTE_KEY))
@Component
public class RagDocStorageConsumer {

    private static final Logger log = LoggerFactory.getLogger(RagDocStorageConsumer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final EmbeddingDomainService embeddingService;
    private final FileDetailDomainService fileDetailDomainService;
    private final DocumentUnitRepository documentUnitRepository;

    @Resource(name = "embeddingTaskExecutor")
    private ThreadPoolTaskExecutor embeddingTaskExecutor;

    @Value("${rag.embedding.parallelism:4}")
    private int embeddingParallelism;

    public RagDocStorageConsumer(EmbeddingDomainService embeddingService,
            FileDetailDomainService fileDetailDomainService, DocumentUnitRepository documentUnitRepository) {
        this.embeddingService = embeddingService;
        this.fileDetailDomainService = fileDetailDomainService;
        this.documentUnitRepository = documentUnitRepository;
    }

    @RabbitHandler
    public void receiveMessage(java.util.Map<String, Object> payload, Message message, Channel channel)
            throws IOException {
        MessageProperties messageProperties = message.getMessageProperties();
        long deliveryTag = messageProperties.getDeliveryTag();

        try {
            // 将已由 Jackson 转换的 Map 转为强类型 Envelope
            MessageEnvelope<RagDocSyncStorageMessage> envelope = OBJECT_MAPPER.convertValue(payload,
                    new TypeReference<MessageEnvelope<RagDocSyncStorageMessage>>() {
                    });

            MDC.put(TRACE_ID, Objects.nonNull(envelope.getTraceId()) ? envelope.getTraceId() : IdWorker.getTimeId());
            RagDocSyncStorageMessage mqRecordReqDTO = envelope.getData();

            if (Boolean.TRUE.equals(mqRecordReqDTO.getBatch())) {
                processBatchEmbedding(mqRecordReqDTO);
            } else {
                log.info("Current file {} page {} - start embedding", mqRecordReqDTO.getFileName(),
                        mqRecordReqDTO.getPage());
                embeddingService.syncStorage(mqRecordReqDTO);
                updateEmbeddingProgress(mqRecordReqDTO);
                log.info("Current file {} page {} - embedding complete", mqRecordReqDTO.getFileName(),
                        mqRecordReqDTO.getPage());
            }

            // 成功处理消息，确认消息
            try {
                if (channel != null && channel.isOpen()) {
                    channel.basicAck(deliveryTag, false);
                }
            } catch (Exception ackException) {
                log.error("确认消息失败", ackException);
            }
        } catch (Exception e) {
            log.error("向量化过程中发生异常", e);
            // 处理失败，拒绝消息并重新入队
            try {
                if (channel != null && channel.isOpen()) {
                    channel.basicNack(deliveryTag, false, true);
                }
            } catch (Exception nackException) {
                log.error("拒绝消息失败", nackException);
            }
        }
    }

    private void processBatchEmbedding(RagDocSyncStorageMessage message) {
        if (message == null || message.getFileId() == null) {
            log.warn("Batch embedding message missing fileId, skip");
            return;
        }
        if (message.getEmbeddingModelConfig() == null) {
            log.error("Batch embedding missing model config, fileId: {}", message.getFileId());
            return;
        }

        String fileId = message.getFileId();
        FileDetailEntity fileEntity = fileDetailDomainService.getFileByIdWithoutUserCheck(fileId);

        List<DocumentUnitEntity> documentUnits = documentUnitRepository
                .selectList(Wrappers.lambdaQuery(DocumentUnitEntity.class)
                        .eq(DocumentUnitEntity::getFileId, fileId)
                        .eq(DocumentUnitEntity::getIsOcr, true)
                        .eq(DocumentUnitEntity::getIsVector, false));

        if (documentUnits.isEmpty()) {
            log.warn("No document units to embed, fileId: {}", fileId);
            return;
        }

        Integer totalPages = fileEntity.getFilePageSize();
        if (totalPages == null || totalPages <= 0) {
            totalPages = documentUnits.size();
        }
        final int totalPagesFinal = totalPages;

        long completedVectorPages = documentUnitRepository
                .selectCount(Wrappers.<DocumentUnitEntity>lambdaQuery()
                        .eq(DocumentUnitEntity::getFileId, fileId)
                        .eq(DocumentUnitEntity::getIsVector, true));
        AtomicInteger completed = new AtomicInteger((int) completedVectorPages);

        int parallelism = Math.max(1, Math.min(embeddingParallelism, documentUnits.size()));

        for (int start = 0; start < documentUnits.size(); start += parallelism) {
            int end = Math.min(start + parallelism, documentUnits.size());
            List<CompletableFuture<Void>> batch = new ArrayList<>(end - start);

            for (int i = start; i < end; i++) {
                DocumentUnitEntity unit = documentUnits.get(i);
                batch.add(CompletableFuture.runAsync(() -> {
                    boolean success = false;
                    try {
                        RagDocSyncStorageMessage unitMessage = new RagDocSyncStorageMessage();
                        unitMessage.setId(unit.getId());
                        unitMessage.setFileId(fileId);
                        unitMessage.setFileName(fileEntity.getOriginalFilename());
                        unitMessage.setPage(unit.getPage());
                        unitMessage.setContent(unit.getContent());
                        unitMessage.setVector(true);
                        unitMessage.setDatasetId(fileEntity.getDataSetId());
                        unitMessage.setUserId(fileEntity.getUserId());
                        unitMessage.setEmbeddingModelConfig(message.getEmbeddingModelConfig());

                        embeddingService.syncStorage(unitMessage);
                        success = true;
                    } catch (Exception e) {
                        log.error("Embedding failed for file {} page {}: {}", fileId, unit.getPage(), e.getMessage());
                    } finally {
                        if (success) {
                            int current = completed.incrementAndGet();
                            updateEmbeddingProgressFast(fileEntity, current, totalPagesFinal);
                        }
                    }
                }, embeddingTaskExecutor));
            }

            CompletableFuture.allOf(batch.toArray(new CompletableFuture[0])).join();
        }
    }

    private void updateEmbeddingProgressFast(FileDetailEntity fileEntity, int currentCompletedPages, int totalPages) {
        if (totalPages <= 0) {
            return;
        }
        int safeCurrent = Math.min(currentCompletedPages, totalPages);
        double progress = ((double) safeCurrent / totalPages) * 100.0;
        fileDetailDomainService.updateFileEmbeddingProgress(fileEntity.getId(), safeCurrent, progress);

        if (safeCurrent >= totalPages) {
            fileDetailDomainService.updateFileEmbeddingProgress(fileEntity.getId(), totalPages, 100.0);
            fileDetailDomainService.completeFileEmbeddingProcessing(fileEntity.getId(), fileEntity.getUserId());
        }
    }

    /** 更新向量化进度
     * @param message 向量化消息 */
    private void updateEmbeddingProgress(RagDocSyncStorageMessage message) {
        try {
            String fileId = message.getFileId();
            Integer pageIndex = message.getPage(); // 这是从0开始的页面索引

            // 获取文件总页数来计算进度
            var fileEntity = fileDetailDomainService.getFileByIdWithoutUserCheck(fileId);
            Integer totalPages = fileEntity.getFilePageSize();

            if (totalPages != null && totalPages > 0) {
                // 查询已完成向量化的页面数量
                long completedVectorPages = documentUnitRepository
                        .selectCount(Wrappers.<DocumentUnitEntity>lambdaQuery()
                                .eq(DocumentUnitEntity::getFileId, fileId).eq(DocumentUnitEntity::getIsVector, true));

                // 当前页面完成后的总完成页数
                int currentCompletedPages = (int) (completedVectorPages + 1);

                // 计算百分比：已完成的页数 / 总页数 * 100
                double progress = ((double) currentCompletedPages / totalPages) * 100.0;

                fileDetailDomainService.updateFileEmbeddingProgress(fileId, currentCompletedPages, progress);
                log.debug("更新文件{}的嵌入进度: {}/{} ({}%)", fileId, currentCompletedPages, totalPages,
                        String.format("%.1f", progress));

                // 检查是否所有页面都已完成向量化
                if (currentCompletedPages >= totalPages) {
                    // 确保进度为100%
                    fileDetailDomainService.updateFileEmbeddingProgress(fileId, totalPages, 100.0);
                    // 通过状态机完成向量化处理
                    fileDetailDomainService.completeFileEmbeddingProcessing(fileId, fileEntity.getUserId());
                    log.info("文件{}的所有页面均已向量化，标记为完成", fileId);
                }
            }
        } catch (Exception e) {
            log.warn("更新文件{}的嵌入进度失败: {}", message.getFileId(), e.getMessage());
        }
    }

}
