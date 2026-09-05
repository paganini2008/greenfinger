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

package com.github.greenfinger.core.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.commons.lang3.StringUtils;
import com.github.greenfinger.core.WebCrawlerConstants;
import crawlercommons.filters.basic.BasicURLNormalizer;
import lombok.experimental.UtilityClass;

/**
 * 
 * @Description: UrlUtils
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@UtilityClass
public class UrlUtils {

    /**
     * RFC 3986 normalisation. Stateless and thread safe.
     */
    private static final BasicURLNormalizer BASIC_NORMALIZER = new BasicURLNormalizer();

    /**
     * Query parameters that identify the visitor or the referral, never the document. Dropping them
     * lets url dedup recognise that two links point at the same page.
     */
    private static final Set<String> TRACKING_PARAMETERS = Set.of("utm_source", "utm_medium",
            "utm_campaign", "utm_term", "utm_content", "utm_id", "gclid", "fbclid", "msclkid",
            "dclid", "yclid", "igshid", "mc_eid", "mc_cid", "_ga", "ref", "referrer", "spm",
            "share_from", "share_source", "from_source");

    public String getProtocol(String url) {
        int index = url.indexOf("://");
        return index > 0 ? url.substring(0, index) : "https";
    }

    public String getHost(String url) {
        try {
            String host = toURI(url).getHost();
            return host != null ? host.toLowerCase(Locale.ROOT) : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * The port a url is served on, or -1 when it is the default one for its scheme. A default port
     * is left out because it is not written in the links either.
     */
    public int getPort(String url) {
        try {
            URI uri = toURI(url);
            int port = uri.getPort();
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
                return -1;
            }
            return port;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * The label a human would call the site by: {@code https://download.csdn.net} is "csdn",
     * {@code https://www.msc.org} is "msc". Used to default a catalog name and its path pattern,
     * matching what the legacy UI put in those fields.
     */
    public String getDomainName(String url) {
        String host = getHost(url);
        if (StringUtils.isBlank(host)) {
            return "";
        }
        String[] labels = host.split("\\.");
        if (labels.length < 2) {
            return labels[0];
        }
        return labels[labels.length - 2];
    }

    /**
     * The default name for a catalog: the host without a leading {@code www.}.
     *
     * <p>
     * Deliberately not {@link #getDomainName(String)}, which returns the registrable label and so
     * gives {@code books.toscrape.com} and {@code quotes.toscrape.com} the same name -- two
     * different sites that would then write their results into one directory.
     */
    public String getSiteName(String url) {
        String host = getHost(url);
        if (StringUtils.isBlank(host)) {
            return "";
        }
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    public URI toURI(String url) throws URISyntaxException {
        return new URI(url.trim());
    }

    public URL toURL(String url) throws Exception {
        return toURI(url).toURL();
    }

    public URL toURL(URL context, String spec) throws Exception {
        return context.toURI().resolve(spec).toURL();
    }

    /**
     * The side requests -- robots.txt and the sitemaps -- as opposed to the pages themselves.
     *
     * It identifies itself. Left to itself {@link URLConnection} sends {@code Java/17.0.2} as the
     * user agent, and a number of large sites answer that with 403 before looking at the path --
     * Wikimedia among them. That produced the worst possible failure: robots.txt came back
     * unreadable, {@link com.github.greenfinger.core.component.acceptor.RobotRuleUrlPathAcceptor}
     * fell back to "no rules" as the protocol says it should for an absent file, and the crawl went
     * ahead ignoring rules the site was publishing all along. The one request that decides whether
     * we are allowed to crawl has to be the one that introduces itself properly.
     *
     * The agent is one of the same {@link WebCrawlerConstants#USER_AGENTS} the extractors send, so
     * a site sees one client rather than a browser asking for pages and something else asking for
     * the rules -- and so the rules that come back are the rules that apply to us.
     */
    public InputStream openStream(URL url, int connectTimeout, int readTimeout) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        connection.setRequestProperty("User-Agent", randomUserAgent());
        connection.setRequestProperty("Accept", "text/plain,text/xml,application/xml,*/*;q=0.8");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        if (connection instanceof HttpURLConnection) {
            ((HttpURLConnection) connection).setInstanceFollowRedirects(true);
        }
        return connection.getInputStream();
    }

    /** One of the pool, so a site is not handed the same string by every node at once. */
    public String randomUserAgent() {
        List<String> agents = WebCrawlerConstants.USER_AGENTS;
        return agents.get(ThreadLocalRandom.current().nextInt(agents.size()));
    }

    /**
     * Canonical form of a url for deduplication purposes, in two passes.
     *
     * <p>
     * The first is syntactic and belongs to the standard: crawler-commons collapses {@code /a/./b/..}
     * into {@code /a}, decodes percent escapes that never needed escaping, and folds repeated
     * slashes. Those are genuinely the same resource by RFC 3986, and without this the crawler
     * fetches {@code /a/./b/../c} and {@code /a/c} as if they were two pages.
     *
     * <p>
     * The second is what deduplication needs and the standard deliberately does not do: a lone
     * trailing slash removed, tracking parameters dropped, the rest sorted, the fragment gone. A
     * standards-compliant normaliser keeps all of those, because {@code /x} and {@code /x/} are
     * allowed to be different resources -- on the web as it is actually built, they are the same
     * page.
     *
     * <p>
     * Returns the input untouched when it cannot be parsed.
     */
    public String normalize(String url) {
        if (StringUtils.isBlank(url)) {
            return url;
        }
        try {
            URI uri = toURI(syntacticallyNormalized(url));
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            int port = uri.getPort();
            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }
            String path = uri.getRawPath();
            if (StringUtils.isBlank(path)) {
                path = "";
            } else if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String query = cleanQuery(uri.getRawQuery());

            StringBuilder str = new StringBuilder();
            if (StringUtils.isNotBlank(scheme)) {
                str.append(scheme).append("://");
            }
            str.append(host);
            if (port > 0) {
                str.append(":").append(port);
            }
            str.append(path);
            if (StringUtils.isNotBlank(query)) {
                str.append("?").append(query);
            }
            return str.toString();
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * The standard's own normalisation, done by crawler-commons rather than by hand. Falls back to
     * the original when it declines to parse, since the second pass can still do its work.
     */
    private String syntacticallyNormalized(String url) {
        try {
            String normalized = BASIC_NORMALIZER.filter(url);
            return StringUtils.isNotBlank(normalized) ? normalized : url;
        } catch (Exception e) {
            return url;
        }
    }

    private String cleanQuery(String rawQuery) {
        if (StringUtils.isBlank(rawQuery)) {
            return "";
        }
        List<String> kept = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            if (StringUtils.isBlank(pair)) {
                continue;
            }
            int index = pair.indexOf('=');
            String name = index > 0 ? pair.substring(0, index) : pair;
            if (TRACKING_PARAMETERS.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            kept.add(pair);
        }
        kept.sort(String::compareTo);
        return String.join("&", kept);
    }

}
