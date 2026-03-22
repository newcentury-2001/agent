package org.xhy.mcpweather;

public class Config {

    private final String host;
    private final int port;
    private final String ssePath;
    private final String messagesPath;
    private final String weatherHost;
    private final String weatherPath;
    private final String appCode;
    private final boolean dockerEnabled;
    private final String dockerImageAllowlist;
    private final boolean dockerPull;
    private final int dockerDefaultTimeoutSec;
    private final int dockerMaxTimeoutSec;
    private final double dockerDefaultCpu;
    private final double dockerMaxCpu;
    private final int dockerDefaultMemoryMb;
    private final int dockerMaxMemoryMb;
    private final String dockerDefaultNetwork;
    private final boolean dockerReadonlyRootfs;

    private Config(String host, int port, String ssePath, String messagesPath,
            String weatherHost, String weatherPath, String appCode,
            boolean dockerEnabled, String dockerImageAllowlist, boolean dockerPull,
            int dockerDefaultTimeoutSec, int dockerMaxTimeoutSec,
            double dockerDefaultCpu, double dockerMaxCpu,
            int dockerDefaultMemoryMb, int dockerMaxMemoryMb,
            String dockerDefaultNetwork, boolean dockerReadonlyRootfs) {
        this.host = host;
        this.port = port;
        this.ssePath = ssePath;
        this.messagesPath = messagesPath;
        this.weatherHost = weatherHost;
        this.weatherPath = weatherPath;
        this.appCode = appCode;
        this.dockerEnabled = dockerEnabled;
        this.dockerImageAllowlist = dockerImageAllowlist;
        this.dockerPull = dockerPull;
        this.dockerDefaultTimeoutSec = dockerDefaultTimeoutSec;
        this.dockerMaxTimeoutSec = dockerMaxTimeoutSec;
        this.dockerDefaultCpu = dockerDefaultCpu;
        this.dockerMaxCpu = dockerMaxCpu;
        this.dockerDefaultMemoryMb = dockerDefaultMemoryMb;
        this.dockerMaxMemoryMb = dockerMaxMemoryMb;
        this.dockerDefaultNetwork = dockerDefaultNetwork;
        this.dockerReadonlyRootfs = dockerReadonlyRootfs;
    }

    public static Config fromEnv() {
        String host = getenvOr("MCP_HOST", "0.0.0.0");
        int port = parseInt(getenvOr("MCP_PORT", "8086"), 8086);
        String ssePath = normalizePath(getenvOr("MCP_SSE_PATH", "/mcp/sse"));
        String messagesPath = normalizePath(getenvOr("MCP_MESSAGES_PATH", "/mcp/messages"));
        String weatherHost = getenvOr("WEATHER_HOST", "https://tinaqi.market.alicloudapi.com");
        String weatherPath = normalizePath(getenvOr("WEATHER_PATH", "/area-to-weather-date"));
        String appCode = System.getenv("ALIYUN_APPCODE");
        if (appCode == null || appCode.isBlank()) {
            throw new IllegalArgumentException("Missing env ALIYUN_APPCODE");
        }
        boolean dockerEnabled = parseBool(getenvOr("MCP_DOCKER_ENABLED", "false"));
        String dockerImageAllowlist = getenvOr("MCP_DOCKER_ALLOWLIST", "");
        boolean dockerPull = parseBool(getenvOr("MCP_DOCKER_PULL", "false"));
        int dockerDefaultTimeoutSec = parseInt(getenvOr("MCP_DOCKER_TIMEOUT_SEC", "60"), 60);
        int dockerMaxTimeoutSec = parseInt(getenvOr("MCP_DOCKER_MAX_TIMEOUT_SEC", "300"), 300);
        double dockerDefaultCpu = parseDouble(getenvOr("MCP_DOCKER_DEFAULT_CPU", "1.0"), 1.0);
        double dockerMaxCpu = parseDouble(getenvOr("MCP_DOCKER_MAX_CPU", "2.0"), 2.0);
        int dockerDefaultMemoryMb = parseInt(getenvOr("MCP_DOCKER_DEFAULT_MEMORY_MB", "512"), 512);
        int dockerMaxMemoryMb = parseInt(getenvOr("MCP_DOCKER_MAX_MEMORY_MB", "2048"), 2048);
        String dockerDefaultNetwork = getenvOr("MCP_DOCKER_DEFAULT_NETWORK", "none");
        boolean dockerReadonlyRootfs = parseBool(getenvOr("MCP_DOCKER_READONLY_ROOTFS", "true"));
        return new Config(host, port, ssePath, messagesPath, weatherHost, weatherPath, appCode,
                dockerEnabled, dockerImageAllowlist, dockerPull, dockerDefaultTimeoutSec, dockerMaxTimeoutSec,
                dockerDefaultCpu, dockerMaxCpu, dockerDefaultMemoryMb, dockerMaxMemoryMb, dockerDefaultNetwork,
                dockerReadonlyRootfs);
    }

    private static String getenvOr(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? defaultValue : v.trim();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean parseBool(String value) {
        return "true".equalsIgnoreCase(value) || "1".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String p = path.trim();
        return p.startsWith("/") ? p : "/" + p;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getSsePath() {
        return ssePath;
    }

    public String getMessagesPath() {
        return messagesPath;
    }

    public String getWeatherHost() {
        return weatherHost;
    }

    public String getWeatherPath() {
        return weatherPath;
    }

    public String getAppCode() {
        return appCode;
    }

    public boolean isDockerEnabled() {
        return dockerEnabled;
    }

    public String getDockerImageAllowlist() {
        return dockerImageAllowlist;
    }

    public boolean isDockerPull() {
        return dockerPull;
    }

    public int getDockerDefaultTimeoutSec() {
        return dockerDefaultTimeoutSec;
    }

    public int getDockerMaxTimeoutSec() {
        return dockerMaxTimeoutSec;
    }

    public double getDockerDefaultCpu() {
        return dockerDefaultCpu;
    }

    public double getDockerMaxCpu() {
        return dockerMaxCpu;
    }

    public int getDockerDefaultMemoryMb() {
        return dockerDefaultMemoryMb;
    }

    public int getDockerMaxMemoryMb() {
        return dockerMaxMemoryMb;
    }

    public String getDockerDefaultNetwork() {
        return dockerDefaultNetwork;
    }

    public boolean isDockerReadonlyRootfs() {
        return dockerReadonlyRootfs;
    }
}
