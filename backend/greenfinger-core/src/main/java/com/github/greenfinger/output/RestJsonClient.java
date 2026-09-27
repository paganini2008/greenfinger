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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import com.github.greenfinger.utils.HttpUtils;
import com.github.greenfinger.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.greenfinger.core.WebCrawlerException;
import com.fasterxml.jackson.core.JsonProcessingException;

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

    private final ObjectMapper objectMapper = JsonUtils.MAPPER;
    private final int readTimeout;
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
        // connectTimeout is no longer this client's to set: one shared client means one connect
        // timeout for the process, and it is configured with the rest of the http settings
        this.authorizationHeader = authorizationHeader;
        this.readTimeout = readTimeout;
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

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public JsonNode get(String url) {
        return send(request("GET", url), url);
    }

    public JsonNode put(String url, Object body) {
        return send(withBody(request("PUT", url), body), url);
    }

    public JsonNode post(String url, Object body) {
        return send(withBody(request("POST", url), body), url);
    }

    /**
     * A POST with no body. Some endpoints -- Elasticsearch's {@code _refresh} among them -- reject
     * a request that carries one, even an empty object.
     */
    public JsonNode post(String url) {
        return send(request("POST", url), url);
    }

    /**
     * Elasticsearch's bulk api takes newline-delimited json rather than a json document.
     */
    public JsonNode postNdjson(String url, String ndjson) {
        HttpUriRequestBase request = request("POST", url);
        request.setEntity(new StringEntity(ndjson,
                ContentType.create("application/x-ndjson", StandardCharsets.UTF_8)));
        request.setHeader("Content-Type", "application/x-ndjson");
        return send(request, url);
    }

    public JsonNode delete(String url) {
        return send(request("DELETE", url), url);
    }

    /**
     * DELETE carrying a body. Weaviate's batch delete needs one, and {@code HttpRequest.DELETE()}
     * refuses to attach it, so the method is set explicitly.
     */
    public JsonNode delete(String url, Object body) {
        return send(withBody(request("DELETE", url), body), url);
    }

    /**
     * @return true when the resource exists, false on 404. Other failures still throw.
     */
    public boolean exists(String url) {
        try {
            HttpUtils.Reply reply = HttpUtils.send(request("GET", url), readTimeout);
            if (reply.status() == 404) {
                return false;
            }
            checkStatus(reply, url);
            return true;
        } catch (WebCrawlerException e) {
            throw e;
        } catch (Exception e) {
            throw new WebCrawlerException("Request failed: " + url, e);
        }
    }

    private HttpUriRequestBase request(String method, String url) {
        HttpUriRequestBase request = new HttpUriRequestBase(method, URI.create(url));
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Accept", "application/json");
        if (StringUtils.isNotBlank(authorization)) {
            request.setHeader(authorizationHeader, authorization);
        }
        return request;
    }

    private HttpUriRequestBase withBody(HttpUriRequestBase request, Object body) {
        try {
            request.setEntity(new StringEntity(objectMapper.writeValueAsString(body),
                    ContentType.APPLICATION_JSON));
            return request;
        } catch (JsonProcessingException e) {
            throw new WebCrawlerException("Cannot serialise request body", e);
        }
    }

    private JsonNode send(HttpUriRequestBase request, String url) {
        try {
            HttpUtils.Reply reply = HttpUtils.send(request, readTimeout);
            checkStatus(reply, url);
            String body = new String(reply.body(), StandardCharsets.UTF_8);
            return StringUtils.isNotBlank(body) ? objectMapper.readTree(body)
                    : objectMapper.createObjectNode();
        } catch (WebCrawlerException e) {
            throw e;
        } catch (Exception e) {
            throw new WebCrawlerException("Request failed: " + url, e);
        }
    }

    private void checkStatus(HttpUtils.Reply reply, String url) {
        if (!reply.isOk()) {
            throw new WebCrawlerException("Request to " + url + " returned " + reply.status() + ": "
                    + StringUtils.abbreviate(new String(reply.body(), StandardCharsets.UTF_8), 500));
        }
    }

}
