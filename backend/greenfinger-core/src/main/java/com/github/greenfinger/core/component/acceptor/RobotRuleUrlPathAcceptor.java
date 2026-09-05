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

import java.io.InputStream;
import java.net.URL;
import org.apache.commons.io.IOUtils;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.WebCrawlerConstants;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlTask;
import com.github.greenfinger.core.utils.UrlUtils;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Honours the site's robots.txt. When the file is missing or unreadable the crawl proceeds, which
 * is the Robots Exclusion Protocol's own default for an absent file.
 * 
 * @Description: RobotRuleUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class RobotRuleUrlPathAcceptor implements UrlPathAcceptor, ManagedBeanLifeCycle {

    private final URL robotsTxtUrl;
    private BaseRobotRules rules;

    @SneakyThrows
    public RobotRuleUrlPathAcceptor(CatalogDetails catalogDetails) {
        this.robotsTxtUrl = UrlUtils.toURL(UrlUtils.toURL(catalogDetails.getUrl()), "/robots.txt");
    }

    @SneakyThrows
    public RobotRuleUrlPathAcceptor(String url) {
        this.robotsTxtUrl = UrlUtils.toURL(UrlUtils.toURL(url), "/robots.txt");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        InputStream robotsTxtStream = null;
        try {
            robotsTxtStream = UrlUtils.openStream(robotsTxtUrl, 10000, 60000);
            byte[] content = robotsTxtStream.readAllBytes();
            rules = new SimpleRobotRulesParser().parseContent(robotsTxtUrl.toString(), content,
                    "text/plain", WebCrawlerConstants.USER_AGENTS);
        } catch (Exception e) {
            // Named, because "not available" covers two very different things and the difference
            // decides whether the crawl that follows is polite or merely unaware: a 404 is a site
            // with no rules, and a 403 is a site with rules we failed to introduce ourselves to.
            if (log.isWarnEnabled()) {
                log.warn("{} could not be read ({}), crawling without robot rules.", robotsTxtUrl,
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
            rules = null;
        } finally {
            IOUtils.closeQuietly(robotsTxtStream);
        }
    }

    @Override
    public boolean accept(CatalogDetails catalogDetails, String referUrl, String url,
            CrawlTask task) {
        return rules == null || rules.isAllowed(url);
    }

    @Override
    public int getOrder() {
        return -1;
    }

}
