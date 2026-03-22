package org.xhy.mcpweather;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

public class WeatherClient {

    private final HttpClient httpClient;
    private final String host;
    private final String path;
    private final String appCode;

    public WeatherClient(String host, String path, String appCode) {
        this.host = host;
        this.path = path;
        this.appCode = appCode;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String query(Map<String, String> queryParams) throws IOException, InterruptedException {
        String url = host + path + "?" + buildQuery(queryParams);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "APPCODE " + appCode)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private static String buildQuery(Map<String, String> queryParams) {
        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String> e : queryParams.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            String key = URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8);
            String val = URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8);
            joiner.add(key + "=" + val);
        }
        return joiner.toString();
    }
}
