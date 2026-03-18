package org.xhy.application.conversation.service.message.agent.handler;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xhy.application.conversation.service.message.agent.analysis.dto.PlanDecisionDTO;
import org.xhy.application.conversation.service.message.agent.event.AgentWorkflowEvent;
import org.xhy.application.conversation.service.message.agent.manager.TaskManager;
import org.xhy.application.conversation.service.message.agent.service.InfoRequirementService;
import org.xhy.application.conversation.service.message.agent.template.AgentPromptTemplates;
import org.xhy.application.conversation.service.message.agent.workflow.AgentWorkflowContext;
import org.xhy.application.conversation.service.message.agent.workflow.AgentWorkflowState;
import org.xhy.domain.conversation.constant.MessageType;
import org.xhy.domain.conversation.constant.Role;
import org.xhy.domain.conversation.model.MessageEntity;
import org.xhy.domain.conversation.service.ContextDomainService;
import org.xhy.domain.conversation.service.MessageDomainService;
import org.xhy.domain.plan.model.PlanEntity;
import org.xhy.domain.plan.model.PlanStatus;
import org.xhy.domain.plan.model.PlanStepEntity;
import org.xhy.domain.plan.model.PlanStepStatus;
import org.xhy.domain.plan.service.PlanDomainService;
import org.xhy.domain.task.model.TaskEntity;
import org.xhy.infrastructure.llm.LLMServiceFactory;
import org.xhy.infrastructure.utils.ModelResponseToJsonUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TaskSplitHandler extends AbstractAgentHandler {

    private static final Logger log = LoggerFactory.getLogger(TaskSplitHandler.class);
    private static final String EXTRA_PLAN = "plan";
    private static final String EXTRA_PLAN_STEPS = "planSteps";
    private static final String EXTRA_PLAN_STEP_BY_TASK = "planStepByTask";

    private final InfoRequirementService infoRequirementService;
    private final PlanDomainService planDomainService;

    public TaskSplitHandler(LLMServiceFactory llmServiceFactory, TaskManager taskManager,
            ContextDomainService contextDomainService, InfoRequirementService infoRequirementService,
            MessageDomainService messageDomainService, PlanDomainService planDomainService) {
        super(llmServiceFactory, taskManager, contextDomainService, messageDomainService);
        this.infoRequirementService = infoRequirementService;
        this.planDomainService = planDomainService;
    }

    @Override
    protected boolean shouldHandle(AgentWorkflowEvent event) {
        return event.getToState() == AgentWorkflowState.TASK_SPLITTING;
    }

    @Override
    protected void transitionToNextState(AgentWorkflowContext<?> context) {
        // handled after planning
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> void processEvent(AgentWorkflowContext<?> contextObj) {
        AgentWorkflowContext<T> context = (AgentWorkflowContext<T>) contextObj;

        infoRequirementService.checkInfoAndWaitIfNeeded(context).thenAccept(infoComplete -> {
            if (infoComplete) {
                doTaskSplitting(context);
            }
        });

        this.setBreak(true);
    }

    private <T> void doTaskSplitting(AgentWorkflowContext<T> context) {
        try {
            PlanData planData = getOrCreatePlan(context);
            if (planData == null || planData.steps.isEmpty()) {
                context.handleError(new RuntimeException("??????"));
                return;
            }

            Map<String, PlanStepEntity> stepByTaskId = new HashMap<>();
            for (PlanStepEntity step : planData.steps) {
                String taskName = step.getTitle() == null || step.getTitle().isBlank()
                        ? "Step " + step.getStepNo()
                        : step.getTitle();
                TaskEntity subTask = taskManager.createSubTask(taskName, context.getParentTask().getId(),
                        context.getChatContext());
                context.addSubTask(taskName, subTask);
                stepByTaskId.put(subTask.getId(), step);
            }

            context.addExtraData(EXTRA_PLAN, planData.plan);
            context.addExtraData(EXTRA_PLAN_STEPS, planData.steps);
            context.addExtraData(EXTRA_PLAN_STEP_BY_TASK, stepByTaskId);

            String planSummary = buildPlanSummary(planData.plan, planData.steps, planData.reused);
            context.getLlmMessageEntity().setContent(planSummary);
            context.getLlmMessageEntity().setMessageType(MessageType.TEXT);

            context.sendMessage(planSummary, MessageType.TEXT);
            context.sendEndMessage(MessageType.TASK_SPLIT_FINISH);

            saveMessageAndUpdateContext(Collections.singletonList(context.getLlmMessageEntity()),
                    context.getChatContext());

            context.transitionTo(AgentWorkflowState.TASK_SPLIT_COMPLETED);
        } catch (Exception e) {
            log.error("Task planning failed", e);
            context.handleError(e);
        }
    }

    private <T> PlanData getOrCreatePlan(AgentWorkflowContext<T> context) {
        String sessionId = context.getChatContext().getSessionId();
        String userId = context.getChatContext().getUserId();

        PlanEntity activePlan = planDomainService.getActivePlan(sessionId, userId);
        if (activePlan != null) {
            List<PlanStepEntity> steps = planDomainService.getPlanSteps(activePlan.getId());
            if (!steps.isEmpty()) {
                return new PlanData(activePlan, steps, true);
            }
        }

        ChatRequest request = buildPlanningRequest(context);
        ChatModel client = getStrandClient(context);
        ChatResponse response = client.chat(request);
        PlanDecisionDTO decision = ModelResponseToJsonUtils.toJson(response.aiMessage().text(), PlanDecisionDTO.class);

        List<PlanStepEntity> steps = new ArrayList<>();
        String title = context.getChatContext().getUserMessage();
        String goal = "";

        if (decision != null && decision.isNeedPlan() && decision.getPlan() != null) {
            PlanDecisionDTO.PlanDTO planDto = decision.getPlan();
            if (planDto.getTitle() != null && !planDto.getTitle().isBlank()) {
                title = planDto.getTitle();
            }
            goal = planDto.getGoal();

            if (planDto.getSteps() != null && !planDto.getSteps().isEmpty()) {
                int idx = 1;
                for (PlanDecisionDTO.PlanStepDTO stepDto : planDto.getSteps()) {
                    PlanStepEntity step = new PlanStepEntity();
                    Integer stepNo = stepDto.getIndex() != null ? stepDto.getIndex() : idx;
                    step.setStepNo(stepNo);
                    step.setTitle(stepDto.getTitle() == null ? ("Step " + stepNo) : stepDto.getTitle());
                    step.setDetail(stepDto.getDetail());
                    step.setStatus(PlanStepStatus.TODO.name());
                    steps.add(step);
                    idx++;
                }
            }
        }

        if (steps.isEmpty()) {
            PlanStepEntity step = new PlanStepEntity();
            step.setStepNo(1);
            step.setTitle(title);
            step.setDetail("??????");
            step.setStatus(PlanStepStatus.TODO.name());
            steps.add(step);
        }

        PlanEntity plan = planDomainService.createPlan(userId, sessionId, title, goal, steps);
        plan.setStatus(PlanStatus.ACTIVE.name());
        return new PlanData(plan, steps, false);
    }

    private <T> ChatRequest buildPlanningRequest(AgentWorkflowContext<T> context) {
        List<ChatMessage> messages = new ArrayList<>();
        for (MessageEntity messageEntity : context.getChatContext().getMessageHistory()) {
            String content = messageEntity.getContent();
            if (messageEntity.getRole() == Role.SYSTEM) {
                messages.add(new SystemMessage(content));
            } else if (messageEntity.getRole() == Role.USER) {
                messages.add(new UserMessage(content));
            } else {
                messages.add(new AiMessage(content));
            }
        }
        messages.add(new SystemMessage(AgentPromptTemplates.getPlanningPrompt()));
        messages.add(new UserMessage(context.getChatContext().getUserMessage()));
        return buildChatRequest(context, messages);
    }

    private String buildPlanSummary(PlanEntity plan, List<PlanStepEntity> steps, boolean reused) {
        StringBuilder summary = new StringBuilder();
        summary.append(reused ? "Using existing plan: " : "Created plan: ");
        if (plan != null && plan.getTitle() != null) {
            summary.append("??: ").append(plan.getTitle()).append(" ");
        }
        int i = 1;
        for (PlanStepEntity step : steps) {
            summary.append(i).append(". ").append(step.getTitle() == null ? "Step" : step.getTitle()).append(" ");
            i++;
        }
        return summary.toString();
    }

    private static class PlanData {
        private final PlanEntity plan;
        private final List<PlanStepEntity> steps;
        private final boolean reused;

        private PlanData(PlanEntity plan, List<PlanStepEntity> steps, boolean reused) {
            this.plan = plan;
            this.steps = steps;
            this.reused = reused;
        }
    }
}
