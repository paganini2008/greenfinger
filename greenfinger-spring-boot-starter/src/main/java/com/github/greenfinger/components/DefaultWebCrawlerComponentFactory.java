/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
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

package com.github.greenfinger.components;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.util.ClassUtils;
import org.springframework.web.client.RestTemplate;
import com.github.doodler.common.cloud.ApplicationInfoHolder;
import com.github.doodler.common.context.ApplicationContextUtils;
import com.github.greenfinger.CatalogDetails;
import com.github.greenfinger.WebCrawlerExtractorProperties;
import com.github.greenfinger.WebCrawlerProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: DefaultWebCrawlerComponentFactory
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
@Slf4j
@SuppressWarnings("all")
public class DefaultWebCrawlerComponentFactory implements WebCrawlerComponentFactory {

    @Autowired
    private ApplicationInfoHolder applicationInfoHolder;

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
    public List<InterruptionChecker> getInterruptionCheckers(CatalogDetails catalogDetails) {
        return new ArrayList<>(List.of(new FetchDurationInterruptionChecker(),
                new MaxFetchSizeInterruptionChecker()));
    }

    @Override
    public List<UrlPathAcceptor> getUrlPathAcceptors(CatalogDetails catalogDetails) {
        List<UrlPathAcceptor> all = new ArrayList<>();
        List<String> urlPathAcceptors = catalogDetails.getUrlPathAcceptors();
        if (CollectionUtils.isNotEmpty(urlPathAcceptors)) {
            all.addAll(urlPathAcceptors.stream().map(cName -> {
                Class<?> requireType;
                try {
                    requireType = ClassUtils.forName(cName,
                            Thread.currentThread().getContextClassLoader());
                    return (UrlPathAcceptor) ApplicationContextUtils.getOrCreateBean(requireType);
                } catch (Exception e) {
                    if (log.isErrorEnabled()) {
                        log.error(e.getMessage(), e);
                    }
                    return null;
                }

            }).filter(o -> o != null && o instanceof UrlPathAcceptor).toList());
        }
        all.addAll(List.of(new RobotRuleUrlPathAcceptor(catalogDetails),
                new MaxFetchDepthUrlPathAcceptor(), new PathMatcherUrlPathAcceptor()));
        return all;
    }

    @Override
    public Extractor getExtractor(CatalogDetails catalogDetails) {
        Extractor extractor = null;
        String extractorType = catalogDetails.getExtractor();
        switch (extractorType.toLowerCase()) {
            case "default":
            case "resttemplate":
                extractor = StringUtils.isNotBlank(catalogDetails.getCredentialHandler())
                        ? new RestTemplateStatefulExtractor(restTemplate,
                                (ExtractorCredentialHandler) getExtractorCredentialHandler(
                                        catalogDetails.getCredentialHandler()),
                                webCrawlerExtractorProperties)
                        : new RestTemplateExtractor(restTemplate, webCrawlerExtractorProperties);
                break;
            case "htmlunit":
                extractor = StringUtils.isNotBlank(catalogDetails.getCredentialHandler())
                        ? new HtmlUnitStatefulExtractor(
                                (ExtractorCredentialHandler) getExtractorCredentialHandler(
                                        catalogDetails.getCredentialHandler()),
                                webCrawlerExtractorProperties)
                        : new HtmlUnitPooledExtractor(webCrawlerExtractorProperties);
                break;
            case "playwright":
                extractor = StringUtils.isNotBlank(catalogDetails.getCredentialHandler())
                        ? new PlaywrighStatefulExtractor(
                                (ExtractorCredentialHandler) getExtractorCredentialHandler(
                                        catalogDetails.getCredentialHandler()),
                                webCrawlerExtractorProperties)
                        : new PlaywrightPooledExtractor(webCrawlerExtractorProperties);
                break;
            case "selenium":
                extractor = StringUtils.isNotBlank(catalogDetails.getCredentialHandler())
                        ? new SeleniumStatefulExtractor(
                                (ExtractorCredentialHandler) getExtractorCredentialHandler(
                                        catalogDetails.getCredentialHandler()),
                                webCrawlerExtractorProperties)
                        : new SeleniumExtractor(webCrawlerExtractorProperties);
                break;
            default:
                throw new UnsupportedOperationException("Unknown Extractor type: " + extractorType);
        }
        extractor = new ThreadWaitExtractor(extractor, ThreadWait.SLEEP);
        if (catalogDetails.getMaxRetryCount() > 0) {
            extractor = new RetryableExtractor(extractor, catalogDetails.getMaxRetryCount());
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
    public ExistingUrlPathFilter getExistingUrlPathFilter(CatalogDetails catalogDetails) {
        String urlPathFilterType = catalogDetails.getUrlPathFilter();
        switch (urlPathFilterType.toLowerCase()) {
            case "rocksdb":
                return new RocksDbUrlPathFilter(catalogDetails.getId(),
                        catalogDetails.getVersion());
            case "redis":
                return new RedisUrlPathFilter(catalogDetails.getId(), catalogDetails.getVersion(),
                        redisConnectionFactory);
            case "redis-bloomfilter":
                return new RedisBloomUrlPathFilter(catalogDetails.getId(),
                        catalogDetails.getVersion(), redisConnectionFactory);
            case "redission-bloomfilter":
                return new RedissionBloomUrlPathFilter(catalogDetails.getId(),
                        catalogDetails.getVersion(), redissonClient);
            default:
                throw new UnsupportedOperationException(
                        "Unknown UrlPathFilter type: " + urlPathFilterType);
        }
    }

    @Override
    public GlobalStateManager getGlobalStateManager(CatalogDetails catalogDetails) {
        return new RedisGlobalStateManager(catalogDetails, redisConnectionFactory,
                applicationInfoHolder.isPrimary());
    }

    @Override
    public ProgressBarSupplier getProgressBarSupplier(CatalogDetails catalogDetails) {
        return new ProgressBarSupplier(catalogDetails,
                new RedisDashboard(catalogDetails, redisConnectionFactory));
    }

}
