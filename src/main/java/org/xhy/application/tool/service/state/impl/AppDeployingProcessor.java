package org.xhy.application.tool.service.state.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xhy.application.container.service.ReviewContainerService;
import org.xhy.application.tool.service.state.AppToolStateProcessor;
import org.xhy.domain.tool.constant.ToolStatus;
import org.xhy.domain.tool.model.ToolEntity;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.mcp_gateway.MCPGatewayService;
import org.xhy.infrastructure.utils.JsonUtils;

import java.util.Map;

/** Deploy tool to MCP gateway (review container). */
public class AppDeployingProcessor implements AppToolStateProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AppDeployingProcessor.class);

    private final MCPGatewayService mcpGatewayService;
    private final ReviewContainerService reviewContainerService;

    public AppDeployingProcessor(MCPGatewayService mcpGatewayService, ReviewContainerService reviewContainerService) {
        this.mcpGatewayService = mcpGatewayService;
        this.reviewContainerService = reviewContainerService;
    }

    @Override
    public ToolStatus getStatus() {
        return ToolStatus.DEPLOYING;
    }

    @Override
    public void process(ToolEntity tool) {
        logger.info("Tool {} entering DEPLOYING state", tool.getId());

        try {
            Map<String, Object> installCommand = tool.getInstallCommand();
            if (installCommand == null || installCommand.isEmpty()) {
                throw new BusinessException("Tool " + tool.getId() + " install command is empty");
            }

            String directSseUrl = extractDirectSseUrl(installCommand);
            if (directSseUrl != null) {
                logger.info("Detected direct MCP SSE URL, skip gateway deploy. toolId={}, url={}", tool.getId(),
                        directSseUrl);
                return;
            }

            String installCommandJson = JsonUtils.toJsonString(installCommand);

            ReviewContainerService.ReviewContainerConnection reviewConnection = reviewContainerService
                    .getReviewContainerConnection();
            boolean deploySuccess = mcpGatewayService.deployTool(installCommandJson, reviewConnection.getIpAddress(),
                    reviewConnection.getPort());

            if (deploySuccess) {
                logger.info("Tool deployed: {}", tool.getId());
            } else {
                throw new BusinessException("MCP Gateway deploy returned non-success status");
            }
        } catch (BusinessException e) {
            logger.error("Deploy tool failed: {} ({})", tool.getName(), tool.getId(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Deploy tool error: {} ({})", tool.getName(), tool.getId(), e);
            throw new BusinessException("Deploy tool failed: " + e.getMessage(), e);
        }
    }

    @Override
    public ToolStatus getNextStatus() {
        return ToolStatus.FETCHING_TOOLS;
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
}
