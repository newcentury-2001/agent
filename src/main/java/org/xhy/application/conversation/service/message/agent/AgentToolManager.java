package org.xhy.application.conversation.service.message.agent;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.PresetParameter;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xhy.application.conversation.service.McpUrlProviderService;
import org.xhy.application.conversation.service.handler.context.ChatContext;
import org.xhy.infrastructure.utils.JsonUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Agent工具管理器 负责创建和管理工具提供者 */
@Component
public class AgentToolManager {

    private static final Logger logger = LoggerFactory.getLogger(AgentToolManager.class);

    private final McpUrlProviderService mcpUrlProviderService;

    public AgentToolManager(McpUrlProviderService mcpUrlProviderService) {
        this.mcpUrlProviderService = mcpUrlProviderService;
    }

    /** 创建工具提供者（支持全局/用户隔离工具自动识别）
     *
     * @param mcpServerNames 工具服务名列表
     * @param toolPresetParams 工具预设参数
     * @param userId 用户ID（关键参数：用于用户隔离工具）
     * @return 工具提供者实例，如果工具列表为空则返回null */
    public ToolProvider createToolProvider(List<String> mcpServerNames,
            Map<String, Map<String, Map<String, String>>> toolPresetParams, String userId) {
        if (mcpServerNames == null || mcpServerNames.isEmpty()) {
            return null;
        }
        // Map的逻辑： 服务商名 - 工具名 - 工具的参数k-v键值对
        List<McpClient> mcpClients = new ArrayList<>();

        for (String mcpServerName : mcpServerNames) {
            String sseUrl = mcpUrlProviderService.getMcpToolUrl(mcpServerName, userId);
            McpTransport transport = new HttpMcpTransport.Builder().sseUrl(sseUrl).logRequests(true).logResponses(true)
                    .timeout(Duration.ofHours(1)).build();

            McpClient mcpClient = new DefaultMcpClient.Builder().transport(transport).build();

            /** 预先设置参数 */
            if (toolPresetParams != null && toolPresetParams.containsKey(mcpServerName)) {
                List<PresetParameter> presetParameters = new ArrayList<>();
                Map<String, Map<String, String>> serverToolParams = toolPresetParams.get(mcpServerName);
                if (serverToolParams != null) {
                    serverToolParams.forEach((toolName, params) -> {
                        presetParameters.add(new PresetParameter(toolName, JsonUtils.toJsonString(params)));
                    });
                }
                mcpClient.presetParameters(presetParameters);
            }

            // Debug: list available tools from this MCP server
            try {
                List<ToolSpecification> toolSpecifications = mcpClient.listTools();
                logger.info("MCP tools loaded: server={}, url={}, count={}, tools={}", mcpServerName, sseUrl,
                        toolSpecifications != null ? toolSpecifications.size() : 0, toolSpecifications);
            } catch (Exception e) {
                logger.warn("MCP tools list failed: server={}, url={}, err={}", mcpServerName, sseUrl, e.getMessage());
            }
            mcpClients.add(mcpClient);
        }
        return McpToolProvider.builder().mcpClients(mcpClients).build();
    }

    /** 获取可用的工具列表
     *
     * @return 工具URL列表 */
    public List<String> getAvailableTools(ChatContext chatContext) {
        return chatContext.getMcpServerNames();
    }
}
