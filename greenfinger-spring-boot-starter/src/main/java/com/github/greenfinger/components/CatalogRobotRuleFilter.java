package com.github.greenfinger.components;

import java.io.InputStream;
import java.net.URL;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.Ordered;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.UrlUtils;
import com.github.greenfinger.CatalogDetails;
import com.github.greenfinger.WebCrawlerConstants;
import com.github.greenfinger.model.Catalog;
import crawlercommons.robots.BaseRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: CatalogRobotRuleFilter
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class CatalogRobotRuleFilter implements UrlPathAcceptor, Ordered, InitializingBean {

    private final URL robotsTxtUrl;

    @SneakyThrows
    public CatalogRobotRuleFilter(CatalogDetails catalogDetails) {
        robotsTxtUrl = UrlUtils.toURL(new URL(catalogDetails.getUrl()), "robots.txt");
    }

    @SneakyThrows
    public CatalogRobotRuleFilter(String url) {
        robotsTxtUrl = new URL(url);
    }

    private BaseRobotRules rules;

    @Override
    public void afterPropertiesSet() throws Exception {
        InputStream robotsTxtStream = null;
        try {
            robotsTxtStream = UrlUtils.openStream(robotsTxtUrl, 10000, 60000);
            SimpleRobotRulesParser parser = new SimpleRobotRulesParser();
            byte[] content = robotsTxtStream.readAllBytes();
            rules = parser.parseContent(robotsTxtUrl.toString(), content, "text/plain",
                    WebCrawlerConstants.userAgents);
        } catch (Exception e) {
            if (log.isWarnEnabled()) {
                log.warn("{} is not available.", robotsTxtUrl);
            }
            rules = null;
        } finally {
            IOUtils.closeQuietly(robotsTxtStream);
        }
    }

    @Override
    public boolean accept(CatalogDetails catalogDetails, String referUrl, String path,
            Packet packet) {
        return rules == null || rules.isAllowed(path);
    }

    @Override
    public int getOrder() {
        return -1;
    }

    public static void main(String[] args) throws Exception {
        Catalog catalog = new Catalog();
        catalog.setUrl("https://www.delish.com");
        CatalogRobotRuleFilter robotRuleFilter =
                new CatalogRobotRuleFilter("https://www.delish.com");
        robotRuleFilter.afterPropertiesSet();
        for (int i = 0; i < 10; i++) {
            boolean result = robotRuleFilter.accept(null, null,
                    "https://www.delish.com/cooking/menus/g63274018/best-recipes-for-in-between-christmas-nye/",
                    null);
            System.out.println(result);
        }
    }
}
