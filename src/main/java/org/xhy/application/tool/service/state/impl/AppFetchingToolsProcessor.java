package org.xhy.application.tool.service.state.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xhy.application.container.service.ReviewContainerService;
import org.xhy.application.tool.service.state.AppToolStateProcessor;
import org.xhy.domain.tool.constant.ToolStatus;
import org.xhy.domain.tool.model.ToolEntity;
import org.xhy.domain.tool.model.config.ToolDefinition;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.mcp_gateway.MCPGatewayService;

import java.util.List;
import java.util.Map;

/** Fetch tool definitions from review container or direct SSE URL. */
public class AppFetchingToolsProcessor implements AppToolStateProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AppFetchingToolsProcessor.class);

    private final MCPGatewayService mcpGatewayService;
    private final ReviewContainerService reviewContainerService;

    public AppFetchingToolsProcessor(MCPGatewayService mcpGatewayService,
            ReviewContainerService reviewContainerService) {
        this.mcpGatewayService = mcpGatewayService;
        this.reviewContainerService = reviewContainerService;
    }

    @Override
    public ToolStatus getStatus() {
        return ToolStatus.FETCHING_TOOLS;
    }

    @Override
    public void process(ToolEntity tool) {
        logger.info("Tool {} entering FETCHING_TOOLS state", tool.getId());

        try {
            Thread.sleep(3000);

            Map<String, Object> installCommand = tool.getInstallCommand();
            if (installCommand == null || installCommand.isEmpty()) {
                throw new BusinessException("Install command is empty");
            }

            String toolName = extractToolName(installCommand);
            if (toolName == null || toolName.isBlank()) {
                throw new BusinessException("Cannot resolve tool name from install command");
            }
            tool.setMcpServerName(toolName);

            String directSseUrl = extractDirectSseUrl(installCommand);
            List<ToolDefinition> toolDefinitions;
            if (directSseUrl != null) {
                logger.info("Fetch tools from direct SSE URL. tool={}, url={}", toolName, directSseUrl);
                toolDefinitions = fetchToolsWithRetry(directSseUrl, 3);
            } else {
                ReviewContainerService.ReviewContainerConnection reviewConnection = reviewContainerService
                        .getReviewContainerConnection();
                toolDefinitions = mcpGatewayService.listToolsFromReviewContainer(toolName,
                        reviewConnection.getIpAddress(), reviewConnection.getPort());
            }

            if (toolDefinitions == null || toolDefinitions.isEmpty()) {
                throw new BusinessException("Tool definitions empty");
            }
            tool.setToolList(toolDefinitions);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Fetching tools interrupted: " + e.getMessage(), e);
        } catch (BusinessException e) {
            logger.error("Fetching tools failed: {} ({})", tool.getName(), tool.getId(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Fetching tools error: {} ({})", tool.getName(), tool.getId(), e);
            throw new BusinessException("Fetching tools failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ToolStatus getNextStatus() {
        return ToolStatus.APPROVED;
    }

    private String extractToolName(Map<String, Object> installCommand) {
        Object serversObj = installCommand.get("mcpServers");
        if (!(serversObj instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> mcpServers = (Map<String, Object>) serversObj;
        if (mcpServers.isEmpty()) {
            return null;
        }
        return mcpServers.keySet().iterator().next();
    }

    private String extractDirectSseUrl(Map<String, Object> installCommand) {
        Object serversObj = installCommand.get("mcpServers");
        if (!(serversObj instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> mcpServers = (Map<String, Object>) serversObj;
        if (mcpServers.isEmpty()) {
            return null;
        }
        Object serverConfigObj = mcpServers.values().iterator().next();
        if (!(serverConfigObj instanceof Map)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> serverConfig = (Map<String, Object>) serverConfigObj;
        Object url = serverConfig.get("url");
        if (url == null) {
            url = serverConfig.get("sseUrl");
        }
        return url == null ? null : String.valueOf(url);
    }

    private List<ToolDefinition> fetchToolsWithRetry(String sseUrl, int maxAttempts) throws Exception {
        Exception last = null;
        for (int i = 1; i <= maxAttempts; i++) {
            try {
                return mcpGatewayService.listToolsBySseUrl(sseUrl);
            } catch (Exception e) {
                last = e;
                logger.warn("List tools failed (attempt {}/{}): {}", i, maxAttempts, e.getMessage());
                if (i < maxAttempts) {
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw ie;
                    }
                }
            }
        }
        throw new BusinessException("List tools failed after " + maxAttempts + " attempts: " + last.getMessage(), last);
    }
}
