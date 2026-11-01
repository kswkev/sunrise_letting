package com.sunriseletting.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Starts the whole web application on a real port, with no data store or domain service,
 * and checks what a browser would load.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WebApplicationIT {

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void homePageIsServedAsHtml() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/");

        assertEquals(200, response.statusCode(), "home page should load in the running app");
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"),
                "home page should be served as HTML");
        assertTrue(response.body().contains("/webjars/htmx.org/dist/htmx.min.js"),
                "home page should reference the HTMX script");
    }

    @Test
    void htmxScriptReferencedByTheLayoutIsServed() throws IOException, InterruptedException {
        HttpResponse<String> response = get("/webjars/htmx.org/dist/htmx.min.js");

        assertEquals(200, response.statusCode(), "HTMX script URL used by the layout should be served from the WebJar");
        assertTrue(response.body().contains("htmx"), "HTMX script URL should return the HTMX library");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
