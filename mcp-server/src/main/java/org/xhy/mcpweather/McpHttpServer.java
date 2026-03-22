package org.xhy.mcpweather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public class McpHttpServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String JSONRPC = "2.0";

    private final Config config;
    private final HttpServer server;
    private final WeatherClient weatherClient;
    private final DockerService dockerService;
    private final CopyOnWriteArrayList<SseClient> sseClients = new CopyOnWriteArrayList<>();

    public McpHttpServer(Config config) throws IOException {
        this.config = config;
        this.server = HttpServer.create(new InetSocketAddress(config.getHost(), config.getPort()), 0);
        this.server.setExecutor(Executors.newCachedThreadPool());
        this.weatherClient = new WeatherClient(config.getWeatherHost(), config.getWeatherPath(), config.getAppCode());
        this.dockerService = config.isDockerEnabled() ? new DockerService(config) : null;
        this.server.createContext(config.getSsePath(), new SseHandler());
        this.server.createContext(config.getMessagesPath(), new MessagesHandler());
    }

    public void start() {
        System.out.println("MCP Weather Server started at http://" + config.getHost() + ":" + config.getPort());
        System.out.println("SSE endpoint: " + config.getSsePath());
        System.out.println("Messages endpoint: " + config.getMessagesPath());
        server.start();
    }

    private final class SseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }

            Headers headers = exchange.getResponseHeaders();
            headers.add("Content-Type", "text/event-stream; charset=utf-8");
            headers.add("Cache-Control", "no-cache");
            headers.add("Connection", "keep-alive");
            headers.add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, 0);

            OutputStream out = exchange.getResponseBody();
            SseClient client = new SseClient(out);
            sseClients.add(client);

            client.sendEvent("endpoint", config.getMessagesPath());
            client.flush();

            try {
                while (!client.isClosed()) {
                    Thread.sleep(5000);
                    client.sendComment("keepalive " + Instant.now());
                    client.flush();
                }
            } catch (InterruptedException ignored) {
                // ignore
            } catch (IOException ioe) {
                client.close();
            } finally {
                sseClients.remove(client);
                client.close();
            }
        }
    }

    private final class MessagesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeCors(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method Not Allowed");
                return;
            }

            JsonNode root = readJson(exchange.getRequestBody());
            String method = root.path("method").asText(null);
            Long id = root.has("id") && !root.get("id").isNull() ? root.get("id").asLong() : null;

            ObjectNode response;
            if (method == null) {
                response = errorResponse(id, -32600, "Invalid Request: missing method");
            } else {
                String normalized = normalizeMethod(method);
                if ("INITIALIZE".equals(normalized)) {
                    response = handleInitialize(id);
                } else if ("TOOLS_LIST".equals(normalized)) {
                    response = handleToolsList(id);
                } else if ("TOOLS_CALL".equals(normalized)) {
                    response = handleToolsCall(id, root.path("params"));
                } else if ("PING".equals(normalized)) {
                    response = okResponse(id, MAPPER.createObjectNode());
                } else if ("NOTIFICATION_INITIALIZED".equals(normalized)
                        || "NOTIFICATIONS_INITIALIZED".equals(normalized)
                        || "NOTIFICATION_CANCELLED".equals(normalized)
                        || "NOTIFICATIONS_CANCELLED".equals(normalized)) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                } else {
                    response = errorResponse(id, -32601, "Method not found: " + method);
                }
            }

            // MCP over SSE expects responses via SSE "message" events.
            broadcastMessage(response);
            sendText(exchange, 200, "");
        }
    }

    private ObjectNode handleInitialize(Long id) {
        ObjectNode result = MAPPER.createObjectNode();
        result.put("protocolVersion", "2024-11-05");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "mcp-weather-server");
        serverInfo.put("version", "0.1.0");
        result.putObject("capabilities").putObject("tools");
        return okResponse(id, result);
    }

    private ObjectNode handleToolsList(Long id) {
        ObjectNode result = MAPPER.createObjectNode();
        ArrayNode tools = result.putArray("tools");

        ObjectNode tool = tools.addObject();
        tool.put("name", "china_weather");
        tool.put("description", "China weather query (Aliyun API)");

        ObjectNode schema = tool.putObject("inputSchema");
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");

        props.putObject("areaCode").put("type", "string").put("description", "Area code, e.g. 530700");
        props.putObject("area").put("type", "string").put("description", "Area name, e.g. Lijiang");
        props.putObject("date").put("type", "string").put("description", "Date in YYYYMMDD, e.g. 20200319");
        props.putObject("need3HourForcast").put("type", "string").put("description", "3-hour forecast flag: 1 or 0");

        ArrayNode required = schema.putArray("required");
        required.add("areaCode");
        required.add("area");
        required.add("date");

        if (config.isDockerEnabled()) {
            ObjectNode docker = tools.addObject();
            docker.put("name", "docker_run");
            docker.put("description", "Run a short-lived Docker container and return logs");
            ObjectNode dockerSchema = docker.putObject("inputSchema");
            dockerSchema.put("type", "object");
            ObjectNode dockerProps = dockerSchema.putObject("properties");
            dockerProps.putObject("image").put("type", "string").put("description", "Docker image, e.g. python:3.11-slim");
            ObjectNode cmdSchema = dockerProps.putObject("cmd");
            cmdSchema.put("type", "array");
            cmdSchema.putObject("items").put("type", "string");
            cmdSchema.put("description", "Command array, e.g. [\"python\",\"-c\",\"print(1)\"]");
            ObjectNode envSchema = dockerProps.putObject("env");
            envSchema.put("type", "object");
            envSchema.putObject("additionalProperties").put("type", "string");
            dockerProps.putObject("workdir").put("type", "string").put("description", "Working directory");
            dockerProps.putObject("timeoutSec").put("type", "integer").put("description", "Timeout seconds");
            dockerProps.putObject("cpu").put("type", "number").put("description", "CPU cores");
            dockerProps.putObject("memoryMb").put("type", "integer").put("description", "Memory MB");
            ObjectNode networkSchema = dockerProps.putObject("network");
            networkSchema.put("type", "string");
            ArrayNode enumNode = networkSchema.putArray("enum");
            enumNode.add("none");
            enumNode.add("bridge");
            dockerProps.putObject("readonlyRootfs").put("type", "boolean").put("description", "Readonly rootfs");
            dockerProps.putObject("confirmPull").put("type", "boolean")
                    .put("description", "Confirm pulling image if not present locally");
            ArrayNode dockerRequired = dockerSchema.putArray("required");
            dockerRequired.add("image");
            dockerRequired.add("cmd");
        }

        return okResponse(id, result);
    }

    private ObjectNode handleToolsCall(Long id, JsonNode params) {
        String name = params.path("name").asText();
        JsonNode args = params.path("arguments");
        if ("china_weather".equalsIgnoreCase(name)) {
            String areaCode = textArg(args, "areaCode");
            String area = textArg(args, "area");
            String date = textArg(args, "date");
            String need3HourForcast = textArg(args, "need3HourForcast");

            if (isBlank(areaCode) || isBlank(area) || isBlank(date)) {
                return errorResponse(id, -32602, "Missing required arguments: areaCode, area, date");
            }

            Map<String, String> query = new HashMap<>();
            query.put("areaCode", areaCode);
            query.put("area", area);
            query.put("date", date);
            if (!isBlank(need3HourForcast)) {
                query.put("need3HourForcast", need3HourForcast);
            }

            try {
                String body = weatherClient.query(query);
                return toolTextResponse(id, body, false);
            } catch (Exception e) {
                return toolTextResponse(id, "Weather API error: " + e.getMessage(), true);
            }
        }
        if ("docker_run".equalsIgnoreCase(name)) {
            return handleDockerRun(id, args);
        }
        return errorResponse(id, -32602, "Unknown tool: " + name);
    }

    private ObjectNode handleDockerRun(Long id, JsonNode args) {
        if (dockerService == null) {
            return errorResponse(id, -32602, "Docker tool is disabled");
        }
        if (args == null || args.isMissingNode() || args.isNull()) {
            return errorResponse(id, -32602, "Missing arguments");
        }
        try {
            DockerService.DockerRunRequest req = new DockerService.DockerRunRequest();
            req.image = textArg(args, "image");
            req.cmd = readStringArray(args.get("cmd"));
            req.env = readStringMap(args.get("env"));
            req.workdir = textArg(args, "workdir");
            req.timeoutSec = intArg(args, "timeoutSec");
            req.cpu = doubleArg(args, "cpu");
            req.memoryMb = intArg(args, "memoryMb");
            req.network = textArg(args, "network");
            req.readonlyRootfs = boolArg(args, "readonlyRootfs");
            req.confirmPull = boolArg(args, "confirmPull");

            DockerService.DockerRunResult result = dockerService.run(req);

            ObjectNode out = MAPPER.createObjectNode();
            out.put("exitCode", result.exitCode);
            out.put("stdout", result.stdout);
            out.put("stderr", result.stderr);
            out.put("durationMs", result.durationMs);
            return toolTextResponse(id, out.toString(), false);
        } catch (Exception e) {
            return toolTextResponse(id, "Docker run error: " + e.getMessage(), true);
        }
    }

    private static String textArg(JsonNode args, String key) {
        if (args == null || args.isMissingNode() || args.get(key) == null || args.get(key).isNull()) {
            return null;
        }
        return args.get(key).asText();
    }

    private static Integer intArg(JsonNode args, String key) {
        if (args == null || args.isMissingNode() || args.get(key) == null || args.get(key).isNull()) {
            return null;
        }
        JsonNode node = args.get(key);
        return node.canConvertToInt() ? node.asInt() : null;
    }

    private static Double doubleArg(JsonNode args, String key) {
        if (args == null || args.isMissingNode() || args.get(key) == null || args.get(key).isNull()) {
            return null;
        }
        JsonNode node = args.get(key);
        return node.isNumber() ? node.asDouble() : null;
    }

    private static Boolean boolArg(JsonNode args, String key) {
        if (args == null || args.isMissingNode() || args.get(key) == null || args.get(key).isNull()) {
            return null;
        }
        JsonNode node = args.get(key);
        return node.isBoolean() ? node.asBoolean() : null;
    }

    private static List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        ArrayNode array = (ArrayNode) node;
        List<String> list = new ArrayList<>();
        for (JsonNode item : array) {
            if (item != null && !item.isNull()) {
                list.add(item.asText());
            }
        }
        return list;
    }

    private static Map<String, String> readStringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Map<String, String> map = new HashMap<>();
        node.fields().forEachRemaining(entry -> {
            String k = entry.getKey();
            JsonNode v = entry.getValue();
            map.put(k, v == null || v.isNull() ? "" : v.asText());
        });
        return map;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static ObjectNode toolTextResponse(Long id, String text, boolean isError) {
        ObjectNode result = MAPPER.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode item = content.addObject();
        item.put("type", "text");
        item.put("text", text == null ? "" : text);
        result.put("isError", isError);
        return okResponse(id, result);
    }

    private static ObjectNode okResponse(Long id, JsonNode result) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("jsonrpc", JSONRPC);
        if (id == null) {
            node.putNull("id");
        } else {
            node.put("id", id);
        }
        node.set("result", result);
        return node;
    }

    private static ObjectNode errorResponse(Long id, int code, String message) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("jsonrpc", JSONRPC);
        if (id == null) {
            node.putNull("id");
        } else {
            node.put("id", id);
        }
        ObjectNode error = node.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return node;
    }

    private static String normalizeMethod(String method) {
        String m = method.trim().replace('/', '_');
        return m.toUpperCase();
    }

    private static JsonNode readJson(InputStream in) throws IOException {
        return MAPPER.readTree(in);
    }

    private static void writeJson(HttpExchange exchange, JsonNode node) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(node);
        Headers headers = exchange.getResponseHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        headers.add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void writeCors(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Headers", "Content-Type,Authorization");
        headers.add("Access-Control-Allow-Methods", "POST,GET,OPTIONS");
    }

    private void broadcastMessage(JsonNode response) {
        if (sseClients.isEmpty()) {
            return;
        }
        String data = response.toString();
        for (SseClient client : sseClients) {
            try {
                client.sendEvent("message", data);
                client.flush();
            } catch (IOException e) {
                client.close();
                sseClients.remove(client);
            }
        }
    }

    private static final class SseClient {
        private final OutputStream out;
        private volatile boolean closed;

        SseClient(OutputStream out) {
            this.out = out;
        }

        void sendEvent(String event, String data) throws IOException {
            if (closed) {
                return;
            }
            String payload = "event: " + event + "\n" + "data: " + data + "\n\n";
            out.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        void sendComment(String comment) throws IOException {
            if (closed) {
                return;
            }
            String payload = ": " + comment + "\n\n";
            out.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        void flush() throws IOException {
            out.flush();
        }

        void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                out.close();
            } catch (IOException ignored) {
                // ignore
            }
        }

        boolean isClosed() {
            return closed;
        }
    }
}
