package org.xhy.application.plan.service;

import org.springframework.stereotype.Service;
import org.xhy.application.plan.dto.PlanViewDTO;
import org.xhy.domain.plan.model.PlanEntity;
import org.xhy.domain.plan.model.PlanStepEntity;
import org.xhy.domain.plan.service.PlanDomainService;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlanAppService {

    private final PlanDomainService planDomainService;

    public PlanAppService(PlanDomainService planDomainService) {
        this.planDomainService = planDomainService;
    }

    public PlanViewDTO getActivePlan(String sessionId, String userId) {
        PlanEntity plan = planDomainService.getActivePlan(sessionId, userId);
        if (plan == null) {
            plan = planDomainService.getLatestPlan(sessionId, userId);
        }
        if (plan == null) {
            return null;
        }
        List<PlanStepEntity> steps = planDomainService.getPlanSteps(plan.getId());

        PlanViewDTO dto = new PlanViewDTO();
        dto.setId(plan.getId());
        dto.setSessionId(plan.getSessionId());
        dto.setTitle(plan.getTitle());
        dto.setGoal(plan.getSummary());
        dto.setStatus(plan.getStatus());
        dto.setCurrentStep(plan.getCurrentStep());

        List<PlanViewDTO.PlanStepViewDTO> stepViews = new ArrayList<>();
        for (PlanStepEntity step : steps) {
            PlanViewDTO.PlanStepViewDTO stepView = new PlanViewDTO.PlanStepViewDTO();
            stepView.setStepNo(step.getStepNo());
            stepView.setTitle(step.getTitle());
            stepView.setDetail(step.getDetail());
            stepView.setStatus(step.getStatus());
            stepView.setResult(step.getResult());
            stepViews.add(stepView);
        }
        dto.setSteps(stepViews);
        return dto;
    }
}
