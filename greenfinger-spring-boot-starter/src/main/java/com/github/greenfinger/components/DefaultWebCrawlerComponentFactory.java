package com.github.greenfinger.components;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.util.ClassUtils;
import org.springframework.web.client.RestTemplate;
import com.github.doodler.common.context.ApplicationContextUtils;
import com.github.greenfinger.WebCrawlerExtractorProperties;
import com.github.greenfinger.WebCrawlerProperties;
import com.github.greenfinger.components.test.ExtractorCredentialHandler;
import com.github.greenfinger.components.test.HtmlUnitStatefulExtractor;
import com.github.greenfinger.components.test.PlaywrighStatefulExtractor;
import com.github.greenfinger.components.test.RestTemplateStatefulExtractor;
import com.github.greenfinger.components.test.SeleniumStatefulExtractor;
import com.github.greenfinger.model.Catalog;
import com.github.greenfinger.model.CatalogIndex;

/**
 * 
 * @Description: DefaultWebCrawlerComponentFactory
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
@SuppressWarnings("all")
public class DefaultWebCrawlerComponentFactory implements WebCrawlerComponentFactory {

    @Autowired
    private WebCrawlerProperties webCrawlerProperties;

    @Autowired
    private WebCrawlerExtractorProperties webCrawlerExtractorProperties;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private RedissonClient redissonClient;

    @Qualifier("defaultRestTemplate")
    @Autowired
    private RestTemplate restTemplate;

    @Override
    public List<InterruptionChecker> getInterruptionCheckers(Catalog catalog,
            CatalogIndex catalogIndex) {
        return List.of(new DurationInterruptionChecker(catalog, webCrawlerProperties),
                new MaxFetchSizeInterruptionChecker(catalog, webCrawlerProperties));
    }

    @Override
    public List<UrlPathAcceptor> getUrlPathAcceptors(Catalog catalog, CatalogIndex catalogIndex) {
        return List.of(new CatalogRobotRuleFilter(catalog),
                new DepthUrlPathAcceptor(catalog, webCrawlerProperties),
                new CatalogPatternUrlPathAcceptor(catalog, webCrawlerProperties));
    }

    @Override
    public Extractor getExtractor(Catalog catalog, CatalogIndex catalogIndex) {
        Extractor extractor = null;
        String extractorType = catalog.getExtractor();
        switch (extractorType) {
            case "default":
            case "resttemplate":
                extractor = StringUtils.isNotBlank(catalog.getCredentialHandler())
                        ? new RestTemplateStatefulExtractor(
                                (ExtractorCredentialHandler) getExtractorCredentialHandler(
                                        catalog.getCredentialHandler()),
                                restTemplate, webCrawlerExtractorProperties)
                        : null;
                break;
            case "htmlunit":
                extractor = StringUtils.isNotBlank(catalog.getCredentialHandler())
                        ? new HtmlUnitStatefulExtractor(
                                (ExtractorCredentialHandler) getExtractorCredentialHandler(
                                        catalog.getCredentialHandler()),
                                webCrawlerExtractorProperties)
                        : new HtmlUnitPooledExtractor(webCrawlerExtractorProperties);
                break;
            case "playwright":
                extractor = StringUtils.isNotBlank(catalog.getCredentialHandler())
                        ? new PlaywrighStatefulExtractor(webCrawlerExtractorProperties)
                        : new PlaywrightPooledExtractor(webCrawlerExtractorProperties);
                break;
            case "selenium":
                extractor = StringUtils.isNotBlank(catalog.getCredentialHandler())
                        ? new SeleniumStatefulExtractor(
                                (ExtractorCredentialHandler) getExtractorCredentialHandler(
                                        catalog.getCredentialHandler()),
                                webCrawlerExtractorProperties)
                        : new SeleniumExtractor(webCrawlerExtractorProperties);
                break;
            default:
                throw new UnsupportedOperationException("Unknown Extractor type: " + extractorType);
        }
        if (catalog.getInterval() != null) {
            extractor = new ThreadWaitExtractor(extractor, ThreadWait.SLEEP);
        }
        if (catalog.getMaxRetryCount() != null) {
            extractor = new RetryableExtractor(extractor, catalog.getMaxRetryCount());
        }
        return extractor;
    }

    private static Object getExtractorCredentialHandler(String className) {
        Class<?> handlerClass;
        try {
            handlerClass =
                    ClassUtils.forName(className, Thread.currentThread().getContextClassLoader());
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        return ApplicationContextUtils.getOrCreateBean(handlerClass);
    }

    @Override
    public ExistingUrlPathFilter getExistingUrlPathFilter(Catalog catalog,
            CatalogIndex catalogIndex) {
        String urlPathFilterType = catalog.getUrlPathFilter();
        switch (urlPathFilterType) {
            case "redis":
                return new RedisUrlPathFilter(catalog.getId(), catalogIndex.getVersion(),
                        redisConnectionFactory);
            case "redis-bloomfilter":
                return new RedisBloomUrlPathFilter(catalog.getId(), catalogIndex.getVersion(),
                        redisConnectionFactory);
            case "redission-bloomfilter":
                return new RedissionBloomUrlPathFilter(catalog.getId(), catalogIndex.getVersion(),
                        redissonClient);
            default:
                throw new UnsupportedOperationException(
                        "Unknown UrlPathFilter type: " + urlPathFilterType);
        }
    }

    @Override
    public Dashboard getDashboard(Catalog catalog, CatalogIndex catalogIndex) {
        return new OneTimeDashboard(catalog.getId(), catalogIndex.getVersion(),
                redisConnectionFactory);
    }

}
