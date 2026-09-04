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

package com.github.greenfinger.shell;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Serves both crawlable html and canned json, so a command can be exercised end to end without any
 * external service.
 * 
 * @Description: TestHttpServer
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class TestHttpServer implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, String[]> routes = new LinkedHashMap<>();
    private final List<String> paths = new ArrayList<>();

    public TestHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public TestHttpServer html(String path, String body) {
        routes.put(path, new String[] {"text/html; charset=UTF-8", body});
        return this;
    }

    /** Answers any path starting with the prefix, which is how the json endpoints are stubbed. */
    public TestHttpServer json(String pathPrefix, String body) {
        routes.put("~" + pathPrefix, new String[] {"application/json", body});
        return this;
    }

    public List<String> requestedPaths() {
        return paths;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        paths.add(path);
        exchange.getRequestBody().readAllBytes();

        String[] route = routes.get(path);
        if (route == null) {
            for (Map.Entry<String, String[]> entry : routes.entrySet()) {
                if (entry.getKey().startsWith("~") && path.startsWith(entry.getKey().substring(1))) {
                    route = entry.getValue();
                    break;
                }
            }
        }
        try {
            if (route == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] payload = route[1].getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", route[0]);
            exchange.sendResponseHeaders(200, payload.length);
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

}
