package org.xhy.application.conversation.service.message.agent.analysis.dto;

import java.util.List;

public class PlanDecisionDTO {

    private boolean needPlan;
    private PlanDTO plan;

    public boolean isNeedPlan() {
        return needPlan;
    }

    public void setNeedPlan(boolean needPlan) {
        this.needPlan = needPlan;
    }

    public PlanDTO getPlan() {
        return plan;
    }

    public void setPlan(PlanDTO plan) {
        this.plan = plan;
    }

    public static class PlanDTO {
        private String title;
        private String goal;
        private List<PlanStepDTO> steps;

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

        public List<PlanStepDTO> getSteps() {
            return steps;
        }

        public void setSteps(List<PlanStepDTO> steps) {
            this.steps = steps;
        }
    }

    public static class PlanStepDTO {
        private Integer index;
        private String title;
        private String detail;
        private String doneCriteria;

        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
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

        public String getDoneCriteria() {
            return doneCriteria;
        }

        public void setDoneCriteria(String doneCriteria) {
            this.doneCriteria = doneCriteria;
        }
    }
}
