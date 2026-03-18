package org.xhy.application.conversation.service.message.agent.handler;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;
import org.xhy.application.conversation.service.message.agent.event.AgentWorkflowEvent;
import org.xhy.application.conversation.service.message.agent.manager.TaskManager;
import org.xhy.application.conversation.service.message.agent.template.AgentPromptTemplates;
import org.xhy.application.conversation.service.message.agent.workflow.AgentWorkflowContext;
import org.xhy.application.conversation.service.message.agent.workflow.AgentWorkflowState;
import org.xhy.domain.conversation.constant.MessageType;
import org.xhy.domain.conversation.model.MessageEntity;
import org.xhy.domain.conversation.service.ContextDomainService;
import org.xhy.domain.conversation.service.MessageDomainService;
import org.xhy.domain.plan.model.PlanEntity;
import org.xhy.domain.plan.model.PlanStatus;
import org.xhy.domain.plan.service.PlanDomainService;
import org.xhy.infrastructure.llm.LLMServiceFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class SummarizeHandler extends AbstractAgentHandler {

    private static final String EXTRA_PLAN = "plan";
    private final PlanDomainService planDomainService;

    public SummarizeHandler(LLMServiceFactory llmServiceFactory, TaskManager taskManager,
            ContextDomainService contextDomainService, MessageDomainService messageDomainService,
            PlanDomainService planDomainService) {
        super(llmServiceFactory, taskManager, contextDomainService, messageDomainService);
        this.planDomainService = planDomainService;
    }

    @Override
    protected boolean shouldHandle(AgentWorkflowEvent event) {
        return event.getToState() == AgentWorkflowState.TASK_EXECUTED;
    }

    @Override
    protected void transitionToNextState(AgentWorkflowContext<?> context) {
        context.transitionTo(AgentWorkflowState.SUMMARIZING);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> void processEvent(AgentWorkflowContext<?> contextObj) {
        AgentWorkflowContext<T> context = (AgentWorkflowContext<T>) contextObj;

        try {
            MessageEntity summaryMessageEntity = createMessageEntity(context, MessageType.TEXT, null, 0);
            String taskSummary = context.buildTaskSummary();

            StreamingChatModel streamingClient = getStreamingClient(context);
            ChatRequest summaryRequest = buildSummaryRequest(context, taskSummary);

            streamingClient.doChat(summaryRequest, new StreamingChatResponseHandler() {
                StringBuilder fullSummary = new StringBuilder();

                @Override
                public void onPartialResponse(String partialResponse) {
                    fullSummary.append(partialResponse);
                    context.sendMessage(partialResponse, MessageType.TEXT);
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    try {
                        TokenUsage tokenUsage = completeResponse.metadata().tokenUsage();
                        Integer outputTokenCount = tokenUsage.outputTokenCount();

                        String summary = completeResponse.aiMessage().text();
                        summaryMessageEntity.setContent(summary);
                        summaryMessageEntity.setTokenCount(outputTokenCount);

                        saveMessageAndUpdateContext(Collections.singletonList(summaryMessageEntity),
                                context.getChatContext());
                        taskManager.completeTask(context.getParentTask(), summary);

                        PlanEntity plan = (PlanEntity) context.getExtraData(EXTRA_PLAN);
                        if (plan != null) {
                            planDomainService.updatePlanSummary(plan, summary);
                            planDomainService.updatePlanStatus(plan, PlanStatus.DONE);
                        }

                        context.sendEndMessage(MessageType.TEXT);
                        context.completeConnection();
                        context.transitionTo(AgentWorkflowState.COMPLETED);

                    } catch (Exception e) {
                        context.handleError(e);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    context.handleError(error);
                }
            });

        } catch (Exception e) {
            context.handleError(e);
        }
    }

    private ChatRequest buildSummaryRequest(AgentWorkflowContext<?> context, String taskResults) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(AgentPromptTemplates.getSummaryPrompt(taskResults)));
        messages.add(new UserMessage("Summarize based on the above results."));
        return buildChatRequest(context, messages);
    }
}
