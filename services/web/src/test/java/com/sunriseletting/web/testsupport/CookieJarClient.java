package com.sunriseletting.web.testsupport;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;

/**
 * A browser's cookie handling for tests over real HTTP: it keeps every cookie by name, including
 * {@code Secure} ones (the JDK's own cookie manager won't send those over http://localhost), and
 * drops any cookie answered with {@code Max-Age=0}. Redirects aren't followed.
 */
public class CookieJarClient {

    private final HttpClient client = HttpClient.newHttpClient();

    private final String baseUrl;

    private final Map<String, String> cookies = new LinkedHashMap<>();

    public CookieJarClient(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public String cookie(String name) {
        return cookies.get(name);
    }

    public void setCookie(String name, String value) {
        cookies.put(name, value);
    }

    public void removeCookie(String name) {
        cookies.remove(name);
    }

    public HttpResponse<String> get(String path, String... headers) throws IOException, InterruptedException {
        return send(request(path, headers).GET().build());
    }

    public HttpResponse<String> post(String path, Map<String, String> values, String... headers) throws IOException, InterruptedException {
        String body = values.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return send(request(path, headers)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    /** The {@code _csrf} value of the first form on a page. */
    public static String csrfToken(HttpResponse<String> page) {
        return Jsoup.parse(page.body()).select("input[name=_csrf]").attr("value");
    }

    /** The whole {@code Set-Cookie} header for a cookie, or null if the response doesn't set it. */
    public static String setCookieHeader(HttpResponse<String> response, String name) {
        List<String> headers = response.headers().allValues("Set-Cookie");
        return headers.stream().filter(header -> header.startsWith(name + "=")).findFirst().orElse(null);
    }

    private HttpRequest.Builder request(String path, String... headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path));
        if (!cookies.isEmpty()) {
            request.header("Cookie", cookies.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("; ")));
        }
        if (headers.length > 0) {
            request.headers(headers);
        }
        return request;
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        for (String header : response.headers().allValues("Set-Cookie")) {
            String pair = header.substring(0, header.indexOf(';') < 0 ? header.length() : header.indexOf(';'));
            String name = pair.substring(0, pair.indexOf('='));
            if (header.contains("Max-Age=0")) {
                cookies.remove(name);
            } else {
                cookies.put(name, pair.substring(pair.indexOf('=') + 1));
            }
        }
        return response;
    }
}
