package org.xhy.application.plan.dto;

import java.util.ArrayList;
import java.util.List;

public class PlanViewDTO {

    private String id;
    private String sessionId;
    private String title;
    private String goal;
    private String status;
    private Integer currentStep;
    private List<PlanStepViewDTO> steps = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(Integer currentStep) {
        this.currentStep = currentStep;
    }

    public List<PlanStepViewDTO> getSteps() {
        return steps;
    }

    public void setSteps(List<PlanStepViewDTO> steps) {
        this.steps = steps;
    }

    public static class PlanStepViewDTO {
        private Integer stepNo;
        private String title;
        private String detail;
        private String status;
        private String result;

        public Integer getStepNo() {
            return stepNo;
        }

        public void setStepNo(Integer stepNo) {
            this.stepNo = stepNo;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }
    }
}
