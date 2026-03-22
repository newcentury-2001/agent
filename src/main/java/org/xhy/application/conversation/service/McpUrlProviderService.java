package org.xhy.application.conversation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xhy.application.container.dto.ContainerDTO;
import org.xhy.application.container.service.ContainerAppService;
import org.xhy.domain.container.constant.ContainerStatus;
import org.xhy.domain.tool.model.ToolEntity;
import org.xhy.domain.tool.service.ToolDomainService;
import org.xhy.infrastructure.exception.BusinessException;
import org.xhy.infrastructure.mcp_gateway.MCPGatewayService;
import org.xhy.infrastructure.utils.JsonUtils;

import java.util.Map;

/** MCP URL provider. */
@Service
public class McpUrlProviderService {

    private static final Logger logger = LoggerFactory.getLogger(McpUrlProviderService.class);

    private final MCPGatewayService mcpGatewayService;
    private final ContainerAppService containerAppService;
    private final ToolDomainService toolDomainService;

    public McpUrlProviderService(MCPGatewayService mcpGatewayService, ContainerAppService containerAppService,
            ToolDomainService toolDomainService) {
        this.mcpGatewayService = mcpGatewayService;
        this.containerAppService = containerAppService;
        this.toolDomainService = toolDomainService;
    }

    /** Get SSE URL, auto select strategy. */
    public String getSSEUrl(String mcpServerName, String userId) {
        String directSseUrl = getDirectSseUrl(mcpServerName, userId);
        if (directSseUrl != null) {
            logger.info("Using direct MCP SSE URL: tool={}, url={}", mcpServerName, maskSensitiveInfo(directSseUrl));
            return directSseUrl;
        }

        boolean isGlobalTool = isGlobalTool(mcpServerName, userId);
        if (isGlobalTool) {
            return buildReviewContainerSSEUrl(mcpServerName);
        }
        return buildUserContainerSSEUrl(mcpServerName, userId);
    }

    /** Get MCP tool URL (may auto create/start container). */
    public String getMcpToolUrl(String mcpServerName, String userId) {
        try {
            return getSSEUrl(mcpServerName, userId);
        } catch (Exception e) {
            logger.error("Get MCP tool URL failed: userId={}, tool={}", userId, mcpServerName, e);
            throw new BusinessException("Cannot connect tool: " + mcpServerName + " - " + e.getMessage());
        }
    }

    private boolean isGlobalTool(String mcpServerName, String userId) {
        try {
            ToolEntity tool = toolDomainService.getToolByServerNameForUsage(mcpServerName, userId);
            return tool != null && tool.isGlobal();
        } catch (Exception e) {
            logger.warn("Cannot determine tool type, fallback to user tool: {}", mcpServerName, e);
            return false;
        }
    }

    private String getDirectSseUrl(String mcpServerName, String userId) {
        try {
            ToolEntity tool = toolDomainService.getToolByServerNameForUsage(mcpServerName, userId);
            if (tool == null || tool.getInstallCommand() == null) {
                return null;
            }
            Object serversObj = tool.getInstallCommand().get("mcpServers");
            if (!(serversObj instanceof Map)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> mcpServers = (Map<String, Object>) serversObj;
            Object serverConfigObj = mcpServers.get(mcpServerName);
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
        } catch (Exception e) {
            logger.warn("Failed to parse direct SSE URL: tool={}", mcpServerName, e);
            return null;
        }
    }

    private String buildUserContainerSSEUrl(String mcpServerName, String userId) {
        try {
            logger.info("Prepare user container tool: userId={}, tool={}", userId, mcpServerName);
            ContainerDTO containerInfo = ensureUserContainerReady(userId);
            String sseUrl = mcpGatewayService.buildUserContainerUrl(mcpServerName, containerInfo.getIpAddress(),
                    containerInfo.getExternalPort());
            deployTool(containerInfo, mcpServerName, userId);
            logger.info("User container tool ready: userId={}, url={}", userId, maskSensitiveInfo(sseUrl));
            return sseUrl;
        } catch (Exception e) {
            logger.error("Build user container SSE URL failed: userId={}, tool={}", userId, mcpServerName, e);
            throw new BusinessException("Cannot connect user tool: " + e.getMessage());
        }
    }

    private String buildReviewContainerSSEUrl(String mcpServerName) {
        try {
            logger.info("Prepare review container tool: tool={}", mcpServerName);
            ContainerDTO containerInfo = ensureReviewContainerReady();
            String sseUrl = mcpGatewayService.buildUserContainerUrl(mcpServerName, containerInfo.getIpAddress(),
                    containerInfo.getExternalPort());
            logger.info("Review container tool ready: tool={}, url={}", mcpServerName, maskSensitiveInfo(sseUrl));
            return sseUrl;
        } catch (Exception e) {
            logger.error("Build review container SSE URL failed: tool={}", mcpServerName, e);
            throw new BusinessException("Cannot connect global tool: " + e.getMessage());
        }
    }

    private ContainerDTO ensureUserContainerReady(String userId) {
        try {
            ContainerDTO userContainer = containerAppService.getUserContainer(userId);
            if (!isContainerHealthy(userContainer)) {
                throw new BusinessException("User container not healthy: " + userContainer.getStatus());
            }
            return userContainer;
        } catch (Exception e) {
            logger.error("Prepare user container failed: userId={}", userId, e);
            throw new BusinessException("User container prepare failed: " + e.getMessage());
        }
    }

    private boolean isContainerHealthy(ContainerDTO container) {
        if (container == null) {
            return false;
        }
        boolean isRunning = ContainerStatus.RUNNING.equals(container.getStatus());
        boolean hasNetworkInfo = container.getIpAddress() != null && container.getExternalPort() != null;
        boolean hasDockerContainerId = container.getDockerContainerId() != null;
        return isRunning && hasNetworkInfo && hasDockerContainerId;
    }

    private void deployTool(ContainerDTO container, String toolName, String userId) {
        try {
            ToolEntity tool = toolDomainService.getToolByServerNameForUsage(toolName, userId);
            if (tool == null) {
                logger.warn("Tool not found: {}", toolName);
                return;
            }
            String installCommandJson = convertInstallCommand(tool.getInstallCommand());
            mcpGatewayService.deployTool(installCommandJson, container.getIpAddress(), container.getExternalPort());
            Thread.sleep(1000L);
        } catch (Exception e) {
            logger.warn("Deploy tool in container failed: tool={}, error={}", toolName, e.getMessage());
        }
    }

    private String convertInstallCommand(Map<String, Object> installCommand) {
        try {
            return JsonUtils.toJsonString(installCommand);
        } catch (Exception e) {
            throw new BusinessException("Install command JSON failed: " + e.getMessage());
        }
    }

    private ContainerDTO ensureReviewContainerReady() {
        try {
            ContainerDTO reviewContainer = containerAppService.getOrCreateReviewContainer();
            if (!isContainerHealthy(reviewContainer)) {
                throw new BusinessException("Review container not healthy: " + reviewContainer.getStatus());
            }
            return reviewContainer;
        } catch (Exception e) {
            logger.error("Prepare review container failed", e);
            throw new BusinessException("Review container prepare failed: " + e.getMessage());
        }
    }

    private String maskSensitiveInfo(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("api_key=[^&]*", "api_key=***");
    }
}
