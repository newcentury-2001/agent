package org.xhy.application.conversation.service.message.agent.template;

import java.util.Map;

/** Prompt templates for agent workflows. */
public class AgentPromptTemplates {

    private static final String SUMMARY_PREFIX = "The following is a summary of user history. Use only if relevant:\n";

    private static final String infoAnalysisPrompt = "You are a task analysis assistant. Determine whether the user provided enough info.\n\n"
            + "Return JSON only:\n"
            + "{\n"
            + "  \"infoComplete\": true/false,\n"
            + "  \"missingInfoPrompt\": \"If incomplete, ask for missing info; otherwise empty\"\n"
            + "}\n";

    private static final String decompositionPrompt = "You are a task planning expert. Split the user request into clear, independent subtasks.\n"
            + "Output one subtask per line, no numbering or extra text.";

    private static final String planningPrompt = "You are a planning assistant. Return a plan only when the user request clearly needs multiple steps or tool usage. Otherwise set needPlan=false.\n"
            + "Return JSON only:\n"
            + "{\n"
            + "  \"needPlan\": true/false,\n"
            + "  \"plan\": {\n"
            + "    \"title\": \"short title\",\n"
            + "    \"goal\": \"goal/expected outcome\",\n"
            + "    \"steps\": [\n"
            + "      {\"index\": 1, \"title\": \"step title\", \"detail\": \"what to do\", \"doneCriteria\": \"done criteria\"}\n"
            + "    ]\n"
            + "  }\n"
            + "}\n"
            + "No extra text.";

    private static final String taskExecutionPrompt = "You are a task execution expert. Execute the current subtask using the given context.\n\n"
            + "User request: %s\n\n"
            + "Current subtask: %s\n\n"
            + "%s\n\n"
            + "Guidelines:\n"
            + "1. Stay aligned with the overall goal.\n"
            + "2. Focus on the current subtask with concrete steps.\n"
            + "3. Provide practical, specific answers.\n"
            + "4. Use Markdown for clarity.";

    private static final String summaryPrompt = "You are a summarizer. Combine subtask results into a final response.\n\n"
            + "Subtask results:\n%s\n\n"
            + "Return a cohesive, actionable answer in Markdown.";

    private static final String analyserMessagePrompt = "Classify whether the user message is a simple Q&A or a multi-step task.\n\n"
            + "Return JSON only:\n"
            + "{\n"
            + "  \"isQuestion\": boolean,\n"
            + "  \"reply\": \"If question, provide the answer in Markdown; otherwise empty\"\n"
            + "}\n\n"
            + "User message: %s";

    public static String getInfoAnalysisPrompt() {
        return infoAnalysisPrompt;
    }

    public static String getAnalyserMessagePrompt(String userMessage) {
        return String.format(analyserMessagePrompt, userMessage);
    }

    public static String getSummaryPrefix() {
        return SUMMARY_PREFIX;
    }

    public static String getDecompositionPrompt() {
        return decompositionPrompt;
    }

    public static String getTaskExecutionPrompt(String userRequest, String currentTask,
            Map<String, String> previousTaskResults, String planContext) {

        StringBuilder previousTasksBuilder = new StringBuilder();
        if (previousTaskResults != null && !previousTaskResults.isEmpty()) {
            previousTasksBuilder.append("Completed subtasks and results:\n");
            previousTaskResults.forEach((task, result) -> {
                previousTasksBuilder.append("- Task: ").append(task).append("\n  Result: ").append(result).append("\n");
            });
            previousTasksBuilder.append("\n");
        }

        String planContextText = planContext == null || planContext.isBlank() ? "" : planContext + "\n\n";
        return String.format(taskExecutionPrompt, userRequest, currentTask, planContextText + previousTasksBuilder);
    }

    public static String getSummaryPrompt(String taskResults) {
        return String.format(summaryPrompt, taskResults);
    }

    public static String getPlanningPrompt() {
        return planningPrompt;
    }
}
