package org.xhy.application.conversation.service.message.agent.handler;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import org.springframework.stereotype.Component;
import org.xhy.application.conversation.service.message.agent.Agent;
import org.xhy.application.conversation.service.message.agent.AgentToolManager;
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
import org.xhy.domain.plan.model.PlanStepEntity;
import org.xhy.domain.plan.model.PlanStepStatus;
import org.xhy.domain.plan.service.PlanDomainService;
import org.xhy.domain.task.constant.TaskStatus;
import org.xhy.domain.task.model.TaskEntity;
import org.xhy.infrastructure.llm.LLMServiceFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class TaskExecutionHandler extends AbstractAgentHandler {
    private static final String EXTRA_PLAN = "plan";
    private static final String EXTRA_PLAN_STEPS = "planSteps";
    private static final String EXTRA_PLAN_STEP_BY_TASK = "planStepByTask";

    private final AgentToolManager toolManager;
    private final PlanDomainService planDomainService;

    public TaskExecutionHandler(LLMServiceFactory llmServiceFactory, AgentToolManager toolManager,
            TaskManager taskManager, ContextDomainService contextDomainService,
            MessageDomainService messageDomainService, PlanDomainService planDomainService) {
        super(llmServiceFactory, taskManager, contextDomainService, messageDomainService);
        this.toolManager = toolManager;
        this.planDomainService = planDomainService;
    }

    @Override
    protected boolean shouldHandle(AgentWorkflowEvent event) {
        return event.getToState() == AgentWorkflowState.TASK_SPLIT_COMPLETED;
    }

    @Override
    protected void transitionToNextState(AgentWorkflowContext<?> context) {
        context.transitionTo(AgentWorkflowState.TASK_EXECUTING);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> void processEvent(AgentWorkflowContext<?> contextObj) {
        AgentWorkflowContext<T> context = (AgentWorkflowContext<T>) contextObj;

        try {
            while (context.hasNextTask()) {
                String taskName = context.getNextTask();
                if (taskName == null) {
                    break;
                }

                TaskEntity subTask = context.getSubTaskMap().get(taskName);
                executeSubTask(context, subTask, taskName, null);

                taskManager.updateTaskProgress(context.getParentTask(), context.getCompletedTaskCount(),
                        context.getTotalTaskCount());
            }

            context.transitionTo(AgentWorkflowState.TASK_EXECUTED);

        } catch (Exception e) {
            context.handleError(e);
        }
    }

    private <T> void executeSubTask(AgentWorkflowContext<T> context, TaskEntity subTask, String taskName,
            ToolProvider toolProvider) {

        PlanEntity plan = (PlanEntity) context.getExtraData(EXTRA_PLAN);
        PlanStepEntity planStep = getPlanStep(context, subTask);
        if (plan != null && planStep != null) {
            planDomainService.updateStepStatus(planStep, PlanStepStatus.DOING, null);
            planDomainService.updateCurrentStep(plan, planStep.getStepNo());
        }

        try {
            String taskId = subTask.getId();
            taskManager.updateTaskStatus(subTask, TaskStatus.IN_PROGRESS);

            MessageEntity taskCallMessageEntity = createMessageEntity(context, MessageType.TASK_EXEC, taskName, 0);
            messageDomainService.saveMessage(Collections.singletonList(taskCallMessageEntity));

            context.sendEndMessage(taskName, MessageType.TASK_EXEC);
            context.sendEndWithTaskIdMessage(taskId, MessageType.TASK_STATUS_TO_LOADING);

            String userRequest = context.getChatContext().getUserMessage();
            Map<String, String> previousTaskResults = context.getTaskResults();
            String planContext = buildPlanContext(plan,
                    (List<PlanStepEntity>) context.getExtraData(EXTRA_PLAN_STEPS));

            String taskPrompt = AgentPromptTemplates.getTaskExecutionPrompt(userRequest, taskName, previousTaskResults,
                    planContext);

            ChatModel strandClient = llmServiceFactory.getStrandClient(context.getChatContext().getProvider(),
                    context.getChatContext().getModel());

            Agent agent = AiServices.builder(Agent.class).chatModel(strandClient).toolProvider(toolProvider).build();

            AiMessage aiMessage = agent.chat(taskPrompt);

            if (aiMessage.hasToolExecutionRequests()) {
                handleToolCalls(aiMessage, context);
            }

            String taskResult = aiMessage.text();

            context.addTaskResult(taskName, taskResult);
            taskManager.completeTask(subTask, taskResult);

            if (plan != null && planStep != null) {
                planDomainService.updateStepStatus(planStep, PlanStepStatus.DONE, taskResult);
            }

            context.sendEndWithTaskIdMessage(taskId, MessageType.TASK_STATUS_TO_FINISH);

        } catch (Exception e) {
            subTask.updateStatus(TaskStatus.FAILED);
            subTask.setTaskResult("??: " + e.getMessage());
            taskManager.updateTaskStatus(subTask, TaskStatus.FAILED);

            if (plan != null && planStep != null) {
                planDomainService.updateStepStatus(planStep, PlanStepStatus.FAILED, e.getMessage());
            }

            context.sendEndMessage("?? '" + taskName + "' ??: " + e.getMessage(), MessageType.TEXT);
            context.addTaskResult(taskName, "??: " + e.getMessage());
        }
    }

    private <T> void handleToolCalls(AiMessage aiMessage, AgentWorkflowContext<T> context) {
        MessageEntity toolCallMessageEntity = createMessageEntity(context, MessageType.TOOL_CALL, null, 0);
        StringBuilder toolCallsContent = new StringBuilder("Tool calls: ");

        aiMessage.toolExecutionRequests().forEach(toolExecutionRequest -> {
            String toolName = toolExecutionRequest.name();
            toolCallsContent.append("- ").append(toolName).append(" ");
            context.sendEndMessage(toolName, MessageType.TOOL_CALL);
        });

        toolCallMessageEntity.setContent(toolCallsContent.toString());
        messageDomainService.saveMessage(Collections.singletonList(toolCallMessageEntity));

        context.getChatContext().getContextEntity().getActiveMessages().add(toolCallMessageEntity.getId());
    }

    private PlanStepEntity getPlanStep(AgentWorkflowContext<?> context, TaskEntity subTask) {
        Object mapObj = context.getExtraData(EXTRA_PLAN_STEP_BY_TASK);
        if (!(mapObj instanceof Map)) {
            return null;
        }
        Map<String, PlanStepEntity> map = (Map<String, PlanStepEntity>) mapObj;
        return map.get(subTask.getId());
    }

    private String buildPlanContext(PlanEntity plan, List<PlanStepEntity> steps) {
        if (plan == null || steps == null || steps.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Current plan: ");
        if (plan.getTitle() != null) {
            sb.append("??: ").append(plan.getTitle()).append(" ");
        }
        for (PlanStepEntity step : steps) {
            sb.append(step.getStepNo()).append(". [").append(step.getStatus()).append("] ")
                    .append(step.getTitle() == null ? "Step" : step.getTitle()).append(" ");
        }
        return sb.toString();
    }
}
