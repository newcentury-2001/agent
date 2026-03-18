package org.xhy.domain.plan.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.xhy.domain.plan.model.PlanEntity;
import org.xhy.domain.plan.model.PlanStatus;
import org.xhy.domain.plan.model.PlanStepEntity;
import org.xhy.domain.plan.model.PlanStepStatus;
import org.xhy.domain.plan.repository.PlanRepository;
import org.xhy.domain.plan.repository.PlanStepRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class PlanDomainService {

    private final PlanRepository planRepository;
    private final PlanStepRepository planStepRepository;

    public PlanDomainService(PlanRepository planRepository, PlanStepRepository planStepRepository) {
        this.planRepository = planRepository;
        this.planStepRepository = planStepRepository;
    }

    public PlanEntity getActivePlan(String sessionId, String userId) {
        return planRepository.selectOne(Wrappers.<PlanEntity>lambdaQuery()
                .eq(PlanEntity::getSessionId, sessionId)
                .eq(PlanEntity::getUserId, userId)
                .in(PlanEntity::getStatus, PlanStatus.PLANNING.name(), PlanStatus.ACTIVE.name())
                .orderByDesc(PlanEntity::getCreatedAt)
                .last("limit 1"));
    }

    public PlanEntity getLatestPlan(String sessionId, String userId) {
        return planRepository.selectOne(Wrappers.<PlanEntity>lambdaQuery()
                .eq(PlanEntity::getSessionId, sessionId)
                .eq(PlanEntity::getUserId, userId)
                .orderByDesc(PlanEntity::getCreatedAt)
                .last("limit 1"));
    }

    public List<PlanStepEntity> getPlanSteps(String planId) {
        if (planId == null) {
            return new ArrayList<>();
        }
        return planStepRepository.selectList(Wrappers.<PlanStepEntity>lambdaQuery()
                .eq(PlanStepEntity::getPlanId, planId)
                .orderByAsc(PlanStepEntity::getStepNo));
    }

    public PlanEntity createPlan(String userId, String sessionId, String title, String goal,
            List<PlanStepEntity> steps) {
        PlanEntity planEntity = new PlanEntity();
        planEntity.setUserId(userId);
        planEntity.setSessionId(sessionId);
        planEntity.setTitle(title);
        planEntity.setSummary(goal);
        planEntity.setCurrentStep(steps.isEmpty() ? 0 : 1);
        planEntity.setStatus(PlanStatus.ACTIVE.name());
        planRepository.checkInsert(planEntity);

        for (PlanStepEntity step : steps) {
            step.setPlanId(planEntity.getId());
            if (step.getStatus() == null) {
                step.setStatus(PlanStepStatus.TODO.name());
            }
            planStepRepository.checkInsert(step);
        }
        return planEntity;
    }

    public void updatePlanStatus(PlanEntity planEntity, PlanStatus status) {
        if (planEntity == null || status == null) {
            return;
        }
        planEntity.setStatus(status.name());
        planRepository.checkedUpdateById(planEntity);
    }

    public void updatePlanSummary(PlanEntity planEntity, String summary) {
        if (planEntity == null) {
            return;
        }
        planEntity.setSummary(summary);
        planRepository.checkedUpdateById(planEntity);
    }

    public void updateCurrentStep(PlanEntity planEntity, Integer currentStep) {
        if (planEntity == null || currentStep == null) {
            return;
        }
        planEntity.setCurrentStep(currentStep);
        planRepository.checkedUpdateById(planEntity);
    }

    public void updateStepStatus(PlanStepEntity step, PlanStepStatus status, String result) {
        if (step == null || status == null) {
            return;
        }
        step.setStatus(status.name());
        if (result != null) {
            step.setResult(result);
        }
        planStepRepository.checkedUpdateById(step);
    }
}
