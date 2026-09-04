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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.Getter;

/**
 * A stand-in for Elasticsearch, Qdrant, Weaviate or an embedding endpoint.
 *
 * <p>
 * These integrations are addressed over REST, so a server returning canned json exercises the
 * client code exactly as the real service would, without the tests depending on any of them being
 * installed, reachable, or at a particular version.
 * 
 * @Description: StubServer
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class StubServer implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, Route> routes = new LinkedHashMap<>();

    /** Every request received, so a test can assert on what was sent. */
    @Getter
    private final List<Request> requests = new ArrayList<>();

    public StubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * Answers any request whose path starts with the given prefix.
     */
    public StubServer on(String method, String pathPrefix, int status, String body) {
        routes.put(method + " " + pathPrefix, new Route(status, (m, p) -> body));
        return this;
    }

    public StubServer on(String method, String pathPrefix, int status,
            BiFunction<String, String, String> body) {
        routes.put(method + " " + pathPrefix, new Route(status, body));
        return this;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(method, path, exchange.getRequestURI().getQuery(), body,
                exchange.getRequestHeaders().getFirst("Authorization")));

        Route route = null;
        for (Map.Entry<String, Route> entry : routes.entrySet()) {
            String[] parts = entry.getKey().split(" ", 2);
            if (parts[0].equals(method) && path.startsWith(parts[1])) {
                route = entry.getValue();
                break;
            }
        }
        try {
            if (route == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] payload = route.body.apply(path, body).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (payload.length == 0) {
                exchange.sendResponseHeaders(route.status, -1);
                return;
            }
            exchange.sendResponseHeaders(route.status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        } finally {
            exchange.close();
        }
    }

    public List<Request> requestsFor(String method, String pathPrefix) {
        return requests.stream()
                .filter(r -> r.method().equals(method) && r.path().startsWith(pathPrefix)).toList();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    /**
     * 
     * @Description: Request
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    public record Request(String method, String path, String query, String body,
            String authorization) {
    }

    /**
     * 
     * @Description: Route
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    private record Route(int status, BiFunction<String, String, String> body) {
    }

}
