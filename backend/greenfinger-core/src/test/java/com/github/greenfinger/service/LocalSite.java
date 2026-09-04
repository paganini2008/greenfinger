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

package com.github.greenfinger.service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A small site served from within the test, so a crawl can be run end to end without the internet.
 * 
 * @Description: LocalSite
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class LocalSite implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, String> pages = new HashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger requests =
            new java.util.concurrent.atomic.AtomicInteger();
    private final Map<String, String> validators = new HashMap<>();
    private final Map<String, byte[]> images = new HashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger notModified =
            new java.util.concurrent.atomic.AtomicInteger();

    public LocalSite() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public LocalSite html(String path, String body) {
        pages.put(path, body);
        return this;
    }

    /**
     * A page that publishes an {@code ETag} and answers 304 when it is offered back -- what a real
     * site does for a merge that has crawled the page before.
     */
    public LocalSite conditional(String path, String body, String etag) {
        validators.put(path, etag);
        return html(path, body);
    }

    /**
     * A picture, so a crawl has something to store and a restore has something to fetch again.
     */
    public LocalSite image(String path, byte[] bytes) {
        images.put(path, bytes);
        return this;
    }

    /** Requests answered without a body, which is the saving a conditional merge is claiming. */
    public int notModifiedCount() {
        return notModified.get();
    }

    /** How many requests the site has served, so a test can prove nothing was fetched again. */
    public int requestCount() {
        return requests.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        String path = exchange.getRequestURI().getPath();
        byte[] image = images.get(path);
        if (image != null) {
            try {
                exchange.getResponseHeaders().set("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, image.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(image);
                }
            } finally {
                exchange.close();
            }
            return;
        }
        String body = pages.get(path);
        try {
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String etag = validators.get(exchange.getRequestURI().getPath());
            if (etag != null) {
                exchange.getResponseHeaders().set("ETag", etag);
                if (etag.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
                    notModified.incrementAndGet();
                    exchange.sendResponseHeaders(304, -1);
                    return;
                }
            }
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
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
