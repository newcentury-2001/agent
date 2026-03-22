package org.xhy.mcpweather;

public class Main {

    public static void main(String[] args) throws Exception {
        Config config = Config.fromEnv();
        McpHttpServer server = new McpHttpServer(config);
        server.start();
    }
}
