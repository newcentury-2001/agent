package org.xhy.mcpweather;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.LogContainerResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DockerService {

    private final DockerClient client;
    private final Set<String> allowlist;
    private final boolean pullOnDemand;
    private final int defaultTimeoutSec;
    private final int maxTimeoutSec;
    private final double defaultCpu;
    private final double maxCpu;
    private final int defaultMemoryMb;
    private final int maxMemoryMb;
    private final String defaultNetwork;
    private final boolean defaultReadonlyRootfs;

    public DockerService(Config config) {
        DefaultDockerClientConfig cfg = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(cfg.getDockerHost())
                .sslConfig(cfg.getSSLConfig())
                .maxConnections(50)
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(300))
                .build();
        this.client = DockerClientImpl.getInstance(cfg, httpClient);
        this.allowlist = parseAllowlist(config.getDockerImageAllowlist());
        this.pullOnDemand = config.isDockerPull();
        this.defaultTimeoutSec = config.getDockerDefaultTimeoutSec();
        this.maxTimeoutSec = config.getDockerMaxTimeoutSec();
        this.defaultCpu = config.getDockerDefaultCpu();
        this.maxCpu = config.getDockerMaxCpu();
        this.defaultMemoryMb = config.getDockerDefaultMemoryMb();
        this.maxMemoryMb = config.getDockerMaxMemoryMb();
        this.defaultNetwork = config.getDockerDefaultNetwork();
        this.defaultReadonlyRootfs = config.isDockerReadonlyRootfs();
    }

    public DockerRunResult run(DockerRunRequest request) {
        String image = requireText(request.image, "image");
        if (!isImageAllowed(image)) {
            throw new IllegalArgumentException("Image not allowed: " + image);
        }
        List<String> cmd = request.cmd == null ? List.of() : request.cmd;
        if (cmd.isEmpty()) {
            throw new IllegalArgumentException("cmd is required");
        }

        int timeoutSec = clampInt(request.timeoutSec == null ? defaultTimeoutSec : request.timeoutSec, 1,
                maxTimeoutSec);
        double cpu = clampDouble(request.cpu == null ? defaultCpu : request.cpu, 0.1, maxCpu);
        int memoryMb = clampInt(request.memoryMb == null ? defaultMemoryMb : request.memoryMb, 64, maxMemoryMb);
        String network = normalizeNetwork(request.network == null ? defaultNetwork : request.network);
        boolean readonlyRootfs = request.readonlyRootfs == null ? defaultReadonlyRootfs : request.readonlyRootfs;

        ensureImage(image, Boolean.TRUE.equals(request.confirmPull));

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withReadonlyRootfs(readonlyRootfs)
                .withNetworkMode(network)
                .withMemory(memoryMb * 1024L * 1024L)
                .withNanoCPUs((long) (cpu * 1_000_000_000L));

        List<String> envList = toEnvList(request.env);

        CreateContainerResponse created = client.createContainerCmd(image)
                .withCmd(cmd)
                .withEnv(envList)
                .withWorkingDir(blankToNull(request.workdir))
                .withHostConfig(hostConfig)
                .exec();

        String containerId = created.getId();
        Instant start = Instant.now();
        int exitCode;
        DockerLogs logs = new DockerLogs();
        try {
            client.startContainerCmd(containerId).exec();
            exitCode = waitForContainer(containerId, timeoutSec);
            logs = readLogs(containerId);
        } finally {
            removeContainer(containerId);
        }

        long durationMs = Duration.between(start, Instant.now()).toMillis();
        return new DockerRunResult(exitCode, logs.stdout, logs.stderr, durationMs);
    }

    private void ensureImage(String image, boolean confirmPull) {
        try {
            InspectImageResponse inspected = client.inspectImageCmd(image).exec();
            if (inspected != null) {
                return;
            }
        } catch (NotFoundException ignored) {
            // fall through
        }
        if (!pullOnDemand) {
            throw new IllegalArgumentException("Image not found locally and pull is disabled: " + image);
        }
        if (!confirmPull) {
            throw new IllegalArgumentException("Image not found locally. Confirm pull by setting confirmPull=true for image: " + image);
        }
        try {
            client.pullImageCmd(image).start().awaitCompletion(300, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Image pull interrupted: " + image);
        }
    }

    private int waitForContainer(String containerId, int timeoutSec) {
        WaitContainerResultCallback callback = new WaitContainerResultCallback();
        try {
            Integer status = client.waitContainerCmd(containerId).exec(callback)
                    .awaitStatusCode(timeoutSec, TimeUnit.SECONDS);
            if (status == null) {
                client.killContainerCmd(containerId).exec();
                return 124;
            }
            return status;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            client.killContainerCmd(containerId).exec();
            return 124;
        } finally {
            callback.close();
        }
    }

    private DockerLogs readLogs(String containerId) {
        DockerLogs logs = new DockerLogs();
        LogContainerResultCallback callback = new LogContainerResultCallback() {
            @Override
            public void onNext(Frame item) {
                if (item != null && item.getPayload() != null) {
                    String text = new String(item.getPayload(), StandardCharsets.UTF_8);
                    if (item.getStreamType() == StreamType.STDERR) {
                        logs.stderr.append(text);
                    } else {
                        logs.stdout.append(text);
                    }
                }
                super.onNext(item);
            }
        };
        try {
            client.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTimestamps(false)
                    .exec(callback)
                    .awaitCompletion(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            callback.close();
        }
        return logs;
    }

    private void removeContainer(String containerId) {
        try {
            client.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception ignored) {
            // ignore cleanup error
        }
    }

    private static List<String> toEnvList(Map<String, String> env) {
        if (env == null || env.isEmpty()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        env.forEach((k, v) -> {
            if (k != null && !k.isBlank()) {
                list.add(k + "=" + (v == null ? "" : v));
            }
        });
        return list;
    }

    private boolean isImageAllowed(String image) {
        if (allowlist.isEmpty()) {
            return false;
        }
        for (String rule : allowlist) {
            if (rule.endsWith("*")) {
                String prefix = rule.substring(0, rule.length() - 1);
                if (image.startsWith(prefix)) {
                    return true;
                }
            } else if (rule.equals(image)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> parseAllowlist(String allowlist) {
        if (allowlist == null || allowlist.isBlank()) {
            return new HashSet<>();
        }
        String[] parts = allowlist.split(",");
        Set<String> set = new HashSet<>();
        for (String p : parts) {
            String v = p == null ? "" : p.trim();
            if (!v.isEmpty()) {
                set.add(v);
            }
        }
        return set;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static String normalizeNetwork(String network) {
        if (network == null || network.isBlank()) {
            return "none";
        }
        String n = network.trim().toLowerCase();
        if (!n.equals("none") && !n.equals("bridge")) {
            return "none";
        }
        return n;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class DockerRunRequest {
        public String image;
        public List<String> cmd;
        public Map<String, String> env;
        public String workdir;
        public Integer timeoutSec;
        public Double cpu;
        public Integer memoryMb;
        public String network;
        public Boolean readonlyRootfs;
        public Boolean confirmPull;
    }

    public static final class DockerRunResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final long durationMs;

        public DockerRunResult(int exitCode, String stdout, String stderr, long durationMs) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.durationMs = durationMs;
        }
    }

    private static final class DockerLogs {
        final StringBuilder stdout = new StringBuilder();
        final StringBuilder stderr = new StringBuilder();
    }
}
