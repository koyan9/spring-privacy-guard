/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

abstract class SampleMultiInstanceHttpTestSupport extends SampleReceiverRouteTestSupport {

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    protected static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    protected static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP_TYPE = new TypeReference<>() {
    };

    protected ConfigurableApplicationContext startNode(
            String jdbcUrl,
            Class<?>[] sources,
            String[] profiles,
            String... args
    ) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(sources).profiles(profiles);
        return builder.run(args);
    }

    protected int port(ConfigurableApplicationContext context) {
        return ((ServletWebServerApplicationContext) context).getWebServer().getPort();
    }

    protected Map<String, Object> getJsonObject(HttpClient client, int port, String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET();
        headers.forEach(builder::header);
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return OBJECT_MAPPER.readValue(response.body(), MAP_TYPE);
    }

    protected List<Map<String, Object>> getJsonArray(HttpClient client, int port, String path, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET();
        headers.forEach(builder::header);
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return OBJECT_MAPPER.readValue(response.body(), LIST_OF_MAP_TYPE);
    }

    protected HttpResponse<String> postJson(HttpClient client, int port, String path, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected String buildFileBackedH2JdbcUrl(Path databasePath) {
        return "jdbc:h2:file:" + databasePath.toAbsolutePath().toString().replace("\\", "/") + ";MODE=PostgreSQL;AUTO_SERVER=TRUE";
    }
}
