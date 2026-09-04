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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import lombok.experimental.UtilityClass;

/**
 * Turns the shorthand a user types into a pattern {@code AntPathMatcher} can evaluate against a
 * whole url.
 *
 * <p>
 * {@code AntPathMatcher} splits on {@code /}, so a pattern has to line up with the url's own
 * structure to match. Writing that out in full every time is tedious, so the shorthand forms are
 * expanded here:
 *
 * <pre>
 *   &#42;&#42;.google.com        becomes   &#42;&#42;://&#42;&#42;.google.com/&#42;&#42;
 *                                  plus   &#42;&#42;://google.com/&#42;&#42;
 *   www.google.com/a/&#42;&#42;    becomes   &#42;&#42;://www.google.com/a/&#42;&#42;
 *   https://&#42;&#42;.msc.&#42;&#42;/&#42;&#42;   unchanged, already fully qualified
 * </pre>
 *
 * The second expansion of the first form is what lets a subdomain pattern match the bare domain
 * as well as its subdomains; on its own the pattern requires a dot before {@code google} and would
 * quietly skip {@code https://google.com/}.
 * 
 * @Description: UrlPathPatterns
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@UtilityClass
public class UrlPathPatterns {

    private static final String ANY_SCHEME = "**://";
    private static final String ANY_PATH = "/**";
    private static final String SUBDOMAIN_PREFIX = "**.";

    /**
     * Expands one shorthand pattern into every fully qualified pattern it stands for. A url matches
     * the original pattern when it matches any one of them.
     */
    public List<String> expand(String pattern) {
        List<String> expanded = new ArrayList<>(2);
        if (StringUtils.isBlank(pattern)) {
            return expanded;
        }
        String trimmed = pattern.trim();
        boolean hasScheme = trimmed.contains("://");
        String hostAndPath = hasScheme ? trimmed.substring(trimmed.indexOf("://") + 3) : trimmed;
        String scheme = hasScheme ? trimmed.substring(0, trimmed.indexOf("://") + 3) : ANY_SCHEME;

        if (!hostAndPath.contains("/")) {
            hostAndPath = hostAndPath + ANY_PATH;
        }
        expanded.add(scheme + hostAndPath);

        // A shorthand subdomain pattern should also cover the bare domain itself. A pattern the
        // user spelled out in full is taken literally: widening it would silently change the reach
        // of an existing 1.x catalog.
        if (!hasScheme && hostAndPath.startsWith(SUBDOMAIN_PREFIX)) {
            expanded.add(scheme + hostAndPath.substring(SUBDOMAIN_PREFIX.length()));
        }
        return expanded;
    }

    public List<String> expandAll(List<String> patterns) {
        Set<String> all = new LinkedHashSet<>();
        if (patterns != null) {
            patterns.forEach(p -> all.addAll(expand(p)));
        }
        return List.copyOf(all);
    }

    /**
     * The pattern a freshly created catalog gets when the user did not supply one: the site's
     * registrable domain and everything under it, on any scheme.
     */
    public String defaultPathPattern(String url) {
        String host = UrlUtils.getHost(url);
        if (StringUtils.isBlank(host)) {
            return "";
        }
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        // The port belongs in the pattern. AntPathMatcher compares the host segment literally, so
        // a pattern built from the bare host matches nothing on a site served anywhere but 80 or
        // 443 -- and a catalog whose pattern matches nothing rejects every link it finds and stops
        // one page into the crawl, having fetched only the url it was given.
        int port = UrlUtils.getPort(url);
        return SUBDOMAIN_PREFIX + (port > 0 ? host + ":" + port : host);
    }

}
