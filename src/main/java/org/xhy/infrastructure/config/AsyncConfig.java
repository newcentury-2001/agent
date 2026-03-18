package org.xhy.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** 异步配置 启用Spring的异步处理功能，用于异步事件处理 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** 专用于记忆抽取与持久化的线程池，避免与其他异步任务互相影响 */
    @Bean(name = "memoryTaskExecutor")
    public ThreadPoolTaskExecutor memoryTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("memory-async-");
        // 繁忙时在调用线程执行，确保不丢任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /** OCR处理线程池，限制单文件内并发，避免压垮OCR模型/IO */
    @Bean(name = "ocrTaskExecutor")
    public ThreadPoolTaskExecutor ocrTaskExecutor(
            @Value("${rag.ocr.thread-pool.core:4}") int corePoolSize,
            @Value("${rag.ocr.thread-pool.max:8}") int maxPoolSize,
            @Value("${rag.ocr.thread-pool.queue:200}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("ocr-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /** Embedding线程池，按文件内并发处理向量化 */
    @Bean(name = "embeddingTaskExecutor")
    public ThreadPoolTaskExecutor embeddingTaskExecutor(
            @Value("${rag.embedding.thread-pool.core:4}") int corePoolSize,
            @Value("${rag.embedding.thread-pool.max:8}") int maxPoolSize,
            @Value("${rag.embedding.thread-pool.queue:200}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("embedding-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /** 翻译/拆分线程池，控制文档单元并行度 */
    @Bean(name = "vectorizationTaskExecutor")
    public ThreadPoolTaskExecutor vectorizationTaskExecutor(
            @Value("${rag.vectorization.thread-pool.core:4}") int corePoolSize,
            @Value("${rag.vectorization.thread-pool.max:8}") int maxPoolSize,
            @Value("${rag.vectorization.thread-pool.queue:200}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("vectorization-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
