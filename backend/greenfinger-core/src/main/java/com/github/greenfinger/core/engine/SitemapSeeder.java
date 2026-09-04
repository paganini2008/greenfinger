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

package com.github.greenfinger.core.engine;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.utils.UrlUtils;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import crawlercommons.sitemaps.AbstractSiteMap;
import crawlercommons.sitemaps.SiteMap;
import crawlercommons.sitemaps.SiteMapIndex;
import crawlercommons.sitemaps.SiteMapParser;
import crawlercommons.sitemaps.SiteMapURL;
import lombok.extern.slf4j.Slf4j;

/**
 * Collects the urls a site publishes about itself, before the crawl starts guessing.
 *
 * <p>
 * Following links from the home page reaches the deep pages eventually, and eventually can be a
 * very long time; a sitemap hands over thousands of them at once. Every serious crawler reads one,
 * and the site put it there to be read.
 *
 * <p>
 * Where to look, in the order the standard says: the {@code Sitemap:} directives in robots.txt
 * first, since that is the declared location, and only then the conventional {@code /sitemap.xml}.
 * Sitemap indexes are followed one level down, which is where the large sites keep theirs.
 *
 * <p>
 * What comes back is only a list of candidates. Every url still goes through the frontier and the
 * acceptors, so the domain boundary, the start url prefix and the path patterns apply exactly as
 * they do to a link found on a page.
 * 
 * @Description: SitemapSeeder
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class SitemapSeeder {

    private final WebCrawlerProperties.Sitemap config;
    private final SiteMapParser parser;

    public SitemapSeeder(WebCrawlerProperties.Sitemap config) {
        this.config = config;
        // partial sitemaps are common and worth taking; strict parsing would discard the lot
        this.parser = new SiteMapParser(false);
    }

    /**
     * @param siteUrl the catalog's url
     * @param explicitSitemapUrl a location given by the user, for a site that keeps it somewhere
     *        unusual; null to discover it
     * @return the urls the site published, in the order it published them
     */
    public List<String> discover(String siteUrl, String explicitSitemapUrl) {
        Set<String> urls = new LinkedHashSet<>();
        for (String sitemapUrl : locationsOf(siteUrl, explicitSitemapUrl)) {
            collect(sitemapUrl, urls, 0);
            if (urls.size() >= config.getMaxUrls()) {
                break;
            }
        }
        if (!urls.isEmpty()) {
            log.info("Sitemap offered {} url(s) for {}", urls.size(), siteUrl);
        }
        return new ArrayList<>(urls);
    }

    /**
     * robots.txt is the declared location and is tried first; {@code /sitemap.xml} is only the
     * convention, and a site that declares one somewhere else means it.
     */
    private List<String> locationsOf(String siteUrl, String explicitSitemapUrl) {
        if (StringUtils.isNotBlank(explicitSitemapUrl)) {
            return List.of(explicitSitemapUrl);
        }
        List<String> locations = new ArrayList<>(fromRobotsTxt(siteUrl));
        try {
            String conventional =
                    UrlUtils.toURL(UrlUtils.toURL(siteUrl), "/sitemap.xml").toString();
            if (!locations.contains(conventional)) {
                locations.add(conventional);
            }
        } catch (Exception e) {
            log.debug("Cannot build the conventional sitemap url for {}: {}", siteUrl,
                    e.getMessage());
        }
        return locations;
    }

    private List<String> fromRobotsTxt(String siteUrl) {
        try {
            URL robotsTxt = UrlUtils.toURL(UrlUtils.toURL(siteUrl), "/robots.txt");
            return sitemapsIn(robotsTxt);
        } catch (Exception e) {
            log.debug("No robots.txt for {}: {}", siteUrl, e.getMessage());
            return List.of();
        }
    }

    private List<String> sitemapsIn(URL robotsTxt) {
        try (InputStream in = UrlUtils.openStream(robotsTxt, config.getConnectTimeout(),
                config.getReadTimeout())) {
            BaseRobotRules rules = new SimpleRobotRulesParser().parseContent(robotsTxt.toString(),
                    in.readAllBytes(), "text/plain", List.of("greenfinger"));
            return rules.getSitemaps() != null ? rules.getSitemaps() : List.of();
        } catch (Exception e) {
            log.debug("No sitemap declared in {}: {}", robotsTxt, e.getMessage());
            return List.of();
        }
    }

    private void collect(String sitemapUrl, Set<String> urls, int depth) {
        if (depth > config.getMaxIndexDepth() || urls.size() >= config.getMaxUrls()) {
            return;
        }
        try {
            URL url = UrlUtils.toURL(sitemapUrl);
            byte[] content;
            try (InputStream in = UrlUtils.openStream(url, config.getConnectTimeout(),
                    config.getReadTimeout())) {
                content = in.readAllBytes();
            }
            AbstractSiteMap sitemap = parser.parseSiteMap(content, url);
            if (sitemap instanceof SiteMapIndex index) {
                // an index points at the real ones; the big sites all look like this
                for (AbstractSiteMap child : index.getSitemaps()) {
                    collect(child.getUrl().toString(), urls, depth + 1);
                }
            } else if (sitemap instanceof SiteMap plain) {
                for (SiteMapURL entry : plain.getSiteMapUrls()) {
                    if (urls.size() >= config.getMaxUrls()) {
                        return;
                    }
                    urls.add(entry.getUrl().toString());
                }
            }
        } catch (Exception e) {
            // a site without a sitemap is the common case, not a failure
            log.debug("Could not read sitemap {}: {}", sitemapUrl, e.getMessage());
        }
    }

}
