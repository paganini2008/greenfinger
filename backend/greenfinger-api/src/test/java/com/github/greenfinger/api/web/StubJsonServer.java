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

package com.github.greenfinger.api.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Stands in for Elasticsearch and the vector store, so the delete and replay paths can be exercised
 * without either being installed.
 * 
 * @Description: StubJsonServer
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public class StubJsonServer implements AutoCloseable {

    private final HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<Route> routes = new ArrayList<>();

    public StubJsonServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public StubJsonServer on(String method, String pathPrefix, int status, String body) {
        routes.add(new Route(method, pathPrefix, status, body));
        return this;
    }

    public List<String> getRequests() {
        return requests;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        requests.add(method + " " + path);
        try (var in = exchange.getRequestBody()) {
            in.readAllBytes();
        }
        Route route = routes.stream()
                .filter(r -> r.method.equalsIgnoreCase(method) && path.startsWith(r.pathPrefix))
                .findFirst().orElse(null);
        try {
            if (route == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] payload = route.body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(route.status, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private record Route(String method, String pathPrefix, int status, String body) {
    }

}
