/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger.output;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.apache.commons.lang3.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.WebCrawlerException;

/**
 * A small JSON-over-HTTP client, shared by the Elasticsearch, Qdrant and embedding integrations.
 *
 * <p>
 * These services are addressed by their REST apis rather than by their official clients on purpose.
 * The Elasticsearch client refuses a server whose major version it does not match, and pinning
 * three vendor clients in one jar drags in three transitive trees; the calls actually needed here
 * -- create, bulk write, search -- are stable across versions and are a few lines each.
 * 
 * @Description: RestJsonClient
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class RestJsonClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Duration readTimeout;
    private final String authorization;

    /**
     * The header the credential goes in.
     *
     * <p>
     * Not every server takes {@code Authorization}. Qdrant reads a header of its own,
     * {@code api-key}, and answers 401 to a bearer token however well formed -- which is a
     * configuration that looks right, in a deployment that only fails once somebody turns
     * authentication on.
     */
    private final String authorizationHeader;

    public RestJsonClient(int connectTimeout, int readTimeout) {
        this(connectTimeout, readTimeout, null);
    }

    public RestJsonClient(int connectTimeout, int readTimeout, String authorization) {
        this(connectTimeout, readTimeout, authorization, "Authorization");
    }

    public RestJsonClient(int connectTimeout, int readTimeout, String authorization,
            String authorizationHeader) {
        this.authorizationHeader = authorizationHeader;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeout))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        this.readTimeout = Duration.ofMillis(readTimeout);
        this.authorization = authorization;
    }

    public static String basicAuth(String username, String password) {
        if (StringUtils.isBlank(username)) {
            return null;
        }
        String token = username + ":" + (password != null ? password : "");
        return "Basic " + Base64.getEncoder()
                .encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public JsonNode get(String url) {
        return send(request(url).GET().build(), url);
    }

    public JsonNode put(String url, Object body) {
        return send(request(url).PUT(bodyOf(body)).build(), url);
    }

    public JsonNode post(String url, Object body) {
        return send(request(url).POST(bodyOf(body)).build(), url);
    }

    /**
     * A POST with no body. Some endpoints -- Elasticsearch's {@code _refresh} among them -- reject
     * a request that carries one, even an empty object.
     */
    public JsonNode post(String url) {
        return send(request(url).POST(HttpRequest.BodyPublishers.noBody()).build(), url);
    }

    /**
     * Elasticsearch's bulk api takes newline-delimited json rather than a json document.
     */
    public JsonNode postNdjson(String url, String ndjson) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(readTimeout).header("Content-Type", "application/x-ndjson")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(ndjson, StandardCharsets.UTF_8));
        if (StringUtils.isNotBlank(authorization)) {
            builder = builder.header(authorizationHeader, authorization);
        }
        return send(builder.build(), url);
    }

    public JsonNode delete(String url) {
        return send(request(url).DELETE().build(), url);
    }

    /**
     * DELETE carrying a body. Weaviate's batch delete needs one, and {@code HttpRequest.DELETE()}
     * refuses to attach it, so the method is set explicitly.
     */
    public JsonNode delete(String url, Object body) {
        try {
            return send(request(url)
                    .method("DELETE", java.net.http.HttpRequest.BodyPublishers
                            .ofString(objectMapper.writeValueAsString(body)))
                    .header("Content-Type", "application/json").build(), url);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new WebCrawlerException("Cannot serialise request body for " + url, e);
        }
    }

    /**
     * @return true when the resource exists, false on 404. Other failures still throw.
     */
    public boolean exists(String url) {
        try {
            HttpResponse<String> response = execute(request(url).GET().build());
            if (response.statusCode() == 404) {
                return false;
            }
            checkStatus(response, url);
            return true;
        } catch (WebCrawlerException e) {
            throw e;
        } catch (Exception e) {
            throw new WebCrawlerException("Request failed: " + url, e);
        }
    }

    private HttpRequest.Builder request(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(readTimeout)
                .header("Content-Type", "application/json").header("Accept", "application/json");
        if (StringUtils.isNotBlank(authorization)) {
            builder = builder.header(authorizationHeader, authorization);
        }
        return builder;
    }

    private HttpRequest.BodyPublisher bodyOf(Object body) {
        try {
            return HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new WebCrawlerException("Cannot serialise request body", e);
        }
    }

    private JsonNode send(HttpRequest request, String url) {
        try {
            HttpResponse<String> response = execute(request);
            checkStatus(response, url);
            String body = response.body();
            return StringUtils.isNotBlank(body) ? objectMapper.readTree(body)
                    : objectMapper.createObjectNode();
        } catch (WebCrawlerException e) {
            throw e;
        } catch (Exception e) {
            throw new WebCrawlerException("Request failed: " + url, e);
        }
    }

    private HttpResponse<String> execute(HttpRequest request) throws Exception {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private void checkStatus(HttpResponse<String> response, String url) {
        if (response.statusCode() / 100 != 2) {
            throw new WebCrawlerException(
                    "Request to " + url + " returned " + response.statusCode() + ": "
                            + StringUtils.abbreviate(response.body(), 500));
        }
    }

}
