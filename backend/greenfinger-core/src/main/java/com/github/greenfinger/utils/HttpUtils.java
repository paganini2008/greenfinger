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

package com.github.greenfinger.utils;

import java.io.IOException;
import java.util.Map;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.apache.commons.lang3.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * The one http client everything that talks over http uses.
 *
 * <p>
 * There were thirteen before this: one inside every {@code RestJsonClient}, one in the image
 * fetcher, one in the document fetcher, one in the model store, and a fresh connection pool for
 * every catalog in the page extractor. All but the page extractor's were a bare
 * {@code java.net.http.HttpClient}, and that is the difference this class exists for: the jdk
 * client connects to a single resolved address and gives up, while Apache's tries the next one.
 * On a host whose first address does not answer -- gnu.org over IPv4 here, or one dead member of a
 * round-robin -- the pages arrived and the images and documents silently did not.
 *
 * <p>
 * Shared rather than a bean, for the same reason {@link JsonUtils} is: the fetchers and the stores
 * are built by hand and never see the container. It is thread safe, and what differs between
 * callers -- the response timeout, the headers, the method -- belongs to the request rather than to
 * the client. The connect timeout and the pool do not: those are about this machine's network, and
 * one answer for the whole process is the right number of answers.
 */
@Slf4j
public class HttpUtils {

    private HttpUtils() {}

    /**
     * What a deployment gets to decide. Taken from {@code greenfinger.extractor.rest-client}, which
     * is where these numbers already lived when only page fetching had them.
     */
    public record Settings(int maxConnectionTotal, int maxConnectionPerRoute, int connectTimeout,
            int socketTimeout, int connectionRequestTimeout, boolean followRedirects,
            String proxyHost, int proxyPort) {

        public static Settings ofDefaults() {
            return new Settings(200, 20, 10_000, 60_000, 10_000, true, null, 0);
        }
    }

    /** Status, content type and body, which is all any caller here reads off a response. */
    public record Reply(int status, String contentType, byte[] body) {

        public boolean isOk() {
            return status / 100 == 2;
        }
    }

    private static volatile CloseableHttpClient client;
    private static volatile PoolingHttpClientConnectionManager connectionManager;

    /**
     * Called once at startup, before anything crawls. Calling it again replaces the client, so a
     * caller that held on to the old one keeps using a closed pool -- nothing does, because
     * everything asks for it per request.
     */
    public static synchronized void configure(Settings settings) {
        CloseableHttpClient previous = client;
        PoolingHttpClientConnectionManager previousManager = connectionManager;
        connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(settings.maxConnectionTotal())
                .setMaxConnPerRoute(settings.maxConnectionPerRoute())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(settings.connectTimeout()))
                        .setSocketTimeout(Timeout.ofMilliseconds(settings.socketTimeout())).build())
                .build();
        var builder = HttpClients.custom().setConnectionManager(connectionManager)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setResponseTimeout(Timeout.ofMilliseconds(settings.socketTimeout()))
                        .setConnectionRequestTimeout(
                                Timeout.ofMilliseconds(settings.connectionRequestTimeout()))
                        .setRedirectsEnabled(settings.followRedirects()).build());
        if (StringUtils.isNotBlank(settings.proxyHost()) && settings.proxyPort() > 0) {
            builder.setProxy(new HttpHost(settings.proxyHost(), settings.proxyPort()));
        }
        client = builder.build();
        closeQuietly(previous, previousManager);
    }

    public static CloseableHttpClient getClient() {
        CloseableHttpClient current = client;
        if (current == null) {
            synchronized (HttpUtils.class) {
                if (client == null) {
                    configure(Settings.ofDefaults());
                }
                current = client;
            }
        }
        return current;
    }

    public static Reply get(String url, int responseTimeout, Map<String, String> headers)
            throws IOException {
        HttpGet request = new HttpGet(url);
        if (headers != null) {
            headers.forEach((name, value) -> {
                if (value != null) {
                    request.setHeader(name, value);
                }
            });
        }
        return send(request, responseTimeout);
    }

    /**
     * The response is read into memory here rather than handed back as a stream: every caller in
     * this project wants the whole body, and a stream that outlives the handler leaks the
     * connection back to nobody.
     */
    public static Reply send(HttpUriRequestBase request, int responseTimeout) throws IOException {
        if (responseTimeout > 0) {
            request.setConfig(RequestConfig.custom()
                    .setResponseTimeout(Timeout.ofMilliseconds(responseTimeout)).build());
        }
        return getClient().execute(request, response -> {
            HttpEntity entity = response.getEntity();
            byte[] body = entity != null ? EntityUtils.toByteArray(entity) : new byte[0];
            String contentType = entity != null && entity.getContentType() != null
                    ? entity.getContentType()
                    : "";
            return new Reply(response.getCode(), contentType, body);
        });
    }

    /**
     * For a body too large to hold: the handler runs while the connection is still open, so a
     * caller that streams to disk -- a model file is hundreds of megabytes -- never buffers it.
     * The stream is closed with the response, so nothing may escape the handler.
     */
    public static <T> T send(HttpUriRequestBase request, int responseTimeout,
            HttpClientResponseHandler<T> handler) throws IOException {
        if (responseTimeout > 0) {
            request.setConfig(RequestConfig.custom()
                    .setResponseTimeout(Timeout.ofMilliseconds(responseTimeout)).build());
        }
        return getClient().execute(request, handler);
    }

    private static void closeQuietly(CloseableHttpClient previous,
            PoolingHttpClientConnectionManager previousManager) {
        try {
            if (previous != null) {
                previous.close();
            }
            if (previousManager != null) {
                previousManager.close();
            }
        } catch (IOException e) {
            log.debug("Could not close the previous http client: {}", e.getMessage());
        }
    }

}
