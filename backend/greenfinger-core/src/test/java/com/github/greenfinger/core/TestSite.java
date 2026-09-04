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

package com.github.greenfinger.core;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A small web site served from within the test, so the crawler can be exercised end to end without
 * touching the internet.
 *
 * <p>
 * Built on the JDK's own http server, which costs no dependency. Crawling a real site in a test
 * would make the suite depend on that site's availability and on it never being redesigned.
 * 
 * @Description: TestSite
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
public class TestSite implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, Page> pages = new HashMap<>();
    private final AtomicInteger requestCount = new AtomicInteger(0);

    public TestSite() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String url(String path) {
        return baseUrl() + path;
    }

    public int requestCount() {
        return requestCount.get();
    }

    public TestSite html(String path, String body) {
        return page(path, "text/html; charset=UTF-8", body.getBytes(StandardCharsets.UTF_8), 200);
    }

    public TestSite html(String path, String body, Charset charset) {
        return page(path, "text/html", body.getBytes(charset), 200);
    }

    public TestSite text(String path, String contentType, String body) {
        return page(path, contentType, body.getBytes(StandardCharsets.UTF_8), 200);
    }

    public TestSite binary(String path, String contentType, byte[] body) {
        return page(path, contentType, body, 200);
    }

    public TestSite status(String path, int status) {
        return page(path, "text/plain", new byte[0], status);
    }

    public TestSite page(String path, String contentType, byte[] body, int status) {
        pages.put(path, new Page(contentType, body, status));
        return this;
    }

    /**
     * A page that publishes an {@code ETag} and answers 304 when it is offered back, which is what
     * a real site does for a merge that has crawled the page before.
     */
    public TestSite conditional(String path, String body, String etag) {
        validators.put(path, etag);
        return html(path, body);
    }

    /** What the last request offered, so a test can assert the header actually went out. */
    public String lastIfNoneMatch() {
        return lastIfNoneMatch.get();
    }

    /** How many requests were answered without a body, which is the saving being claimed. */
    public int notModifiedCount() {
        return notModified.get();
    }

    private final java.util.Map<String, String> validators = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicReference<String> lastIfNoneMatch =
            new java.util.concurrent.atomic.AtomicReference<>("");
    private final java.util.concurrent.atomic.AtomicInteger notModified =
            new java.util.concurrent.atomic.AtomicInteger();

    private void handle(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        String path = exchange.getRequestURI().getPath();
        Page page = pages.get(path);
        String offered = exchange.getRequestHeaders().getFirst("If-None-Match");
        lastIfNoneMatch.set(offered == null ? "" : offered);
        try {
            if (page == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String etag = validators.get(path);
            if (etag != null) {
                exchange.getResponseHeaders().set("ETag", etag);
                if (etag.equals(offered)) {
                    notModified.incrementAndGet();
                    exchange.sendResponseHeaders(304, -1);
                    return;
                }
            }
            exchange.getResponseHeaders().set("Content-Type", page.contentType);
            if (page.body.length == 0) {
                exchange.sendResponseHeaders(page.status, -1);
                return;
            }
            exchange.sendResponseHeaders(page.status, page.body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(page.body);
            }
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    /**
     * 
     * @Description: Page
     * @Author: Fred Feng
     * @Date: 29/08/2026
     * @Version 2.0.0
     */
    private record Page(String contentType, byte[] body, int status) {
    }

    /**
     * A one by one pixel PNG, small enough to inline and real enough for ImageIO to decode.
     */
    public static byte[] onePixelPng() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }

    /**
     * A 200 by 200 PNG, above the default minimum size the image filter enforces.
     */
    public static byte[] largePng() throws IOException {
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(200, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        graphics.setColor(java.awt.Color.BLUE);
        graphics.fillRect(0, 0, 200, 200);
        graphics.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

}
