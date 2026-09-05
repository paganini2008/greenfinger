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

package com.github.greenfinger.core.component.acceptor;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.core.WebCrawlerConstants;
import com.sun.net.httpserver.HttpServer;

/**
 * The rules are only honoured if they can be read, and reading them is an http request like any
 * other -- which is the whole point of these tests.
 *
 * <p>
 * A number of large sites, Wikimedia among them, answer 403 to a request whose user agent is
 * {@code Java/17} without looking at the path. Left to itself that is what {@code URLConnection}
 * sends, and the result was the worst possible outcome: robots.txt came back unreadable, the
 * acceptor fell back to "no rules" the way the protocol says it should for an absent file, and the
 * crawl proceeded ignoring rules the site was publishing all along.
 *
 * @Description: RobotRuleUrlPathAcceptorTest
 * @Author: Fred Feng
 * @Date: 05/09/2026
 * @Version 2.0.0
 */
class RobotRuleUrlPathAcceptorTest {

    private static final String ROBOTS_TXT = """
            User-agent: *
            Disallow: /private
            Allow: /
            """;

    private HttpServer server;
    private final AtomicReference<String> lastUserAgent = new AtomicReference<>();

    /** Serves robots.txt only to a caller that introduced itself, and 403s everyone else. */
    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/robots.txt", exchange -> {
            String agent = exchange.getRequestHeaders().getFirst("User-Agent");
            lastUserAgent.set(agent);
            byte[] body;
            int status;
            if (agent != null && agent.startsWith("Mozilla/")) {
                body = ROBOTS_TXT.getBytes(StandardCharsets.UTF_8);
                status = 200;
            } else {
                body = "forbidden".getBytes(StandardCharsets.UTF_8);
                status = 403;
            }
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.setExecutor(null);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    @DisplayName("robots.txt is fetched with a real user agent, so a site that screens on it answers")
    void introducesItself() throws Exception {
        RobotRuleUrlPathAcceptor acceptor = new RobotRuleUrlPathAcceptor(baseUrl() + "/index.html");
        acceptor.afterPropertiesSet();

        assertThat(lastUserAgent.get()).isIn(WebCrawlerConstants.USER_AGENTS);
    }

    @Test
    @DisplayName("a disallowed path is refused once the rules have actually been read")
    void honoursTheRulesItCouldNotReadBefore() throws Exception {
        RobotRuleUrlPathAcceptor acceptor = new RobotRuleUrlPathAcceptor(baseUrl() + "/index.html");
        acceptor.afterPropertiesSet();

        assertThat(acceptor.accept(null, null, baseUrl() + "/private/secret.html", null)).isFalse();
        assertThat(acceptor.accept(null, null, baseUrl() + "/public/page.html", null)).isTrue();
    }

    @Test
    @DisplayName("a site with no robots.txt at all is crawled, which is the protocol's own default")
    void anAbsentFileMeansNoRules() throws Exception {
        // a port nothing is listening on: unreachable rather than merely empty
        RobotRuleUrlPathAcceptor acceptor =
                new RobotRuleUrlPathAcceptor("http://127.0.0.1:1/index.html");
        acceptor.afterPropertiesSet();

        assertThat(acceptor.accept(null, null, "http://127.0.0.1:1/private/secret.html", null))
                .isTrue();
    }
}
