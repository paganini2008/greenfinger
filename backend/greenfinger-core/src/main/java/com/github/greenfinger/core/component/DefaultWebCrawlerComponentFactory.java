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

package com.github.greenfinger.core.component;

import java.io.File;
import java.util.ArrayList;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.document.DocumentContentParsers;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.util.ClassUtils;
import com.github.greenfinger.core.WebCrawlerConstants;
import com.github.greenfinger.core.WebCrawlerExtractorProperties;
import com.github.greenfinger.core.WebCrawlerProperties;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.model.ExtractorType;
import com.github.greenfinger.core.component.acceptor.AssetUrlPathAcceptor;
import com.github.greenfinger.core.component.acceptor.DomainScopeUrlPathAcceptor;
import com.github.greenfinger.core.component.extractor.AdaptiveExtractor;
import com.github.greenfinger.core.component.extractor.RenderingDetector;
import com.github.greenfinger.core.component.acceptor.MaxFetchDepthUrlPathAcceptor;
import com.github.greenfinger.core.component.acceptor.PathMatcherUrlPathAcceptor;
import com.github.greenfinger.core.component.acceptor.RobotRuleUrlPathAcceptor;
import com.github.greenfinger.core.component.acceptor.StartUrlPrefixUrlPathAcceptor;
import com.github.greenfinger.core.component.acceptor.UrlPathAcceptor;
import com.github.greenfinger.core.component.dedup.ContentDedupFilter;
import com.github.greenfinger.core.component.dedup.ExistingUrlPathFilter;
import com.github.greenfinger.core.component.dedup.RocksDbUrlPathFilter;
import com.github.greenfinger.core.component.dedup.Sha256ContentDedupFilter;
import com.github.greenfinger.core.component.dedup.SimHashContentDedupFilter;
import com.github.greenfinger.core.component.extractor.Extractor;
import com.github.greenfinger.core.component.extractor.HtmlUnitPooledExtractor;
import com.github.greenfinger.core.component.extractor.PlaywrightPooledExtractor;
import com.github.greenfinger.core.component.extractor.RestClientExtractor;
import com.github.greenfinger.core.component.extractor.RetryableExtractor;
import com.github.greenfinger.core.component.extractor.SeleniumExtractor;
import com.github.greenfinger.core.component.extractor.ThreadWaitExtractor;
import com.github.greenfinger.core.component.completion.FetchDurationCompletionChecker;
import com.github.greenfinger.core.component.completion.CompletionChecker;
import com.github.greenfinger.core.component.completion.MaxFetchSizeCompletionChecker;
import com.github.greenfinger.core.component.state.DefaultGlobalStateManager;
import com.github.greenfinger.core.component.state.GlobalStateManager;
import com.github.greenfinger.core.engine.CrawlFrontier;
import com.github.greenfinger.core.engine.RocksDbCrawlFrontier;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: DefaultWebCrawlerComponentFactory
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
@Slf4j
@AllArgsConstructor
public class DefaultWebCrawlerComponentFactory implements WebCrawlerComponentFactory {

    private final WebCrawlerProperties webCrawlerProperties;
    private final WebCrawlerExtractorProperties extractorProperties;

    /**
     * What can read a linked file. Only the built-in text formats unless the application published
     * a DocumentContentParser bean; see docs/developer-guide.md.
     */
    private final DocumentContentParsers documentContentParsers;

    public DefaultWebCrawlerComponentFactory(WebCrawlerProperties webCrawlerProperties,
            WebCrawlerExtractorProperties extractorProperties) {
        this(webCrawlerProperties, extractorProperties, new DocumentContentParsers(List.of()));
    }

    @Override
    public List<CompletionChecker> getCompletionCheckers(CatalogDetails catalogDetails) {
        return new ArrayList<>(List.of(new FetchDurationCompletionChecker(),
                new MaxFetchSizeCompletionChecker()));
    }

    @Override
    public List<UrlPathAcceptor> getUrlPathAcceptors(CatalogDetails catalogDetails) {
        List<UrlPathAcceptor> all = new ArrayList<>();
        // the two boundary acceptors are not configurable and cannot be removed: everything else
        // narrows what is already inside the boundary, these two draw it
        all.add(new DomainScopeUrlPathAcceptor());
        all.add(new StartUrlPrefixUrlPathAcceptor());
        // a link to a file is not a page to crawl. Pictures still arrive: they are read from the
        // markup by the page parser, never by following a link
        all.add(new AssetUrlPathAcceptor(webCrawlerProperties.getSkipExtensions(),
                fetchableDocumentTypes()));
        List<String> customAcceptors = catalogDetails.getUrlPathAcceptors();
        if (CollectionUtils.isNotEmpty(customAcceptors)) {
            for (String className : customAcceptors) {
                UrlPathAcceptor acceptor = instantiate(className);
                if (acceptor != null) {
                    all.add(acceptor);
                }
            }
        }
        // Robots is not optional and is ordered first, so a disallowed url is never even considered
        all.add(new RobotRuleUrlPathAcceptor(catalogDetails));
        all.add(new MaxFetchDepthUrlPathAcceptor());
        all.add(new PathMatcherUrlPathAcceptor(catalogDetails));
        return all;
    }

    private UrlPathAcceptor instantiate(String className) {
        try {
            Class<?> requiredType =
                    ClassUtils.forName(className, Thread.currentThread().getContextClassLoader());
            Object instance = BeanUtils.instantiateClass(requiredType);
            return instance instanceof UrlPathAcceptor ? (UrlPathAcceptor) instance : null;
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("Cannot load custom UrlPathAcceptor '{}': {}", className,
                        e.getMessage());
            }
            return null;
        }
    }

    @Override
    public Extractor getExtractor(CatalogDetails catalogDetails) {
        ExtractorType extractorType = catalogDetails.getExtractor();
        Extractor extractor = extractorType == ExtractorType.ADAPTIVE ? adaptive()
                : engineOf(extractorType.getRepr());
        extractor = new ThreadWaitExtractor(extractor, catalogDetails.getThreadWait());
        if (catalogDetails.getMaxRetryCount() > 0) {
            extractor = new RetryableExtractor(extractor, catalogDetails.getMaxRetryCount());
        }
        return extractor;
    }

    private Extractor engineOf(String extractorType) {
        return switch (extractorType) {
            case WebCrawlerConstants.ENGINE_DEFAULT, WebCrawlerConstants.ENGINE_RESTCLIENT,
                    WebCrawlerConstants.ENGINE_RESTTEMPLATE ->
                new RestClientExtractor(extractorProperties);
            case WebCrawlerConstants.ENGINE_HTMLUNIT ->
                new HtmlUnitPooledExtractor(extractorProperties);
            case WebCrawlerConstants.ENGINE_PLAYWRIGHT ->
                new PlaywrightPooledExtractor(extractorProperties);
            case WebCrawlerConstants.ENGINE_SELENIUM -> new SeleniumExtractor(extractorProperties);
            default -> throw new UnsupportedOperationException(
                    "Unknown Extractor type: " + extractorType);
        };
    }

    /**
     * The browser engines are optional dependencies, so the configured one may simply not be on the
     * classpath. Selenium is the fallback: it is the one a deployment is most likely to have, since
     * it drives a browser that is already installed rather than shipping its own.
     */
    private String availableBrowser(String preferred) {
        if (isAvailable(preferred)) {
            return preferred;
        }
        for (String candidate : WebCrawlerConstants.BROWSER_FALLBACK_ORDER) {
            if (isAvailable(candidate)) {
                log.warn("Extractor '{}' is not on the classpath; falling back to '{}'", preferred,
                        candidate);
                return candidate;
            }
        }
        return null;
    }

    /**
     * The document formats a crawl may fetch, which is only ever formats something can read.
     *
     * <p>
     * Empty unless {@code greenfinger.document.enabled} is on. Naming a format with no parser is
     * refused here, at startup: the alternative is a crawl that fetches every pdf on a site and
     * stores nothing from any of them, which looks like working and is not. Adding the parser is
     * one bean -- see docs/developer-guide.md.
     */
    private Set<String> fetchableDocumentTypes() {
        WebCrawlerProperties.Document config = webCrawlerProperties.getDocument();
        if (!config.isEnabled()) {
            return Set.of();
        }
        Set<String> readable = documentContentParsers.readable();
        if (StringUtils.isBlank(config.getFileTypes())) {
            return readable;
        }
        Set<String> asked = Arrays.stream(StringUtils.split(config.getFileTypes(), ","))
                .map(String::trim).map(one -> StringUtils.removeStart(one, "."))
                .filter(StringUtils::isNotBlank).map(one -> one.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> missing = new LinkedHashSet<>(asked);
        missing.removeAll(readable);
        if (!missing.isEmpty()) {
            throw new WebCrawlerException("greenfinger.document.file-types names " + missing
                    + ", and nothing here can read " + (missing.size() == 1 ? "it" : "them")
                    + ". Register a DocumentContentParser bean for it, or take it off the list."
                    + " Readable now: " + readable);
        }
        return asked;
    }

    private boolean isAvailable(String engine) {
        String className = switch (engine) {
            case WebCrawlerConstants.ENGINE_HTMLUNIT -> WebCrawlerConstants.CLASS_HTMLUNIT;
            case WebCrawlerConstants.ENGINE_PLAYWRIGHT -> WebCrawlerConstants.CLASS_PLAYWRIGHT;
            case WebCrawlerConstants.ENGINE_SELENIUM -> WebCrawlerConstants.CLASS_SELENIUM;
            default -> null;
        };
        if (className == null) {
            return false;
        }
        try {
            Class.forName(className, false, getClass().getClassLoader());
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Plain http first, a browser only for the pages that came back as an unrendered shell. Which
     * browser is configurable -- all three engines work here, and none of them is started unless a
     * page actually needs one.
     */
    private Extractor adaptive() {
        WebCrawlerExtractorProperties.Adaptive config = extractorProperties.getAdaptive();
        String requested = config.getBrowser().toLowerCase();
        if (WebCrawlerConstants.ENGINE_RESTCLIENT.equals(requested)
                || WebCrawlerConstants.ENGINE_DEFAULT.equals(requested)) {
            throw new UnsupportedOperationException(
                    "adaptive needs a browser to fall back to: htmlunit, playwright or selenium");
        }
        String browser = availableBrowser(requested);
        if (browser == null) {
            // The browser engines are optional dependencies, and adaptive is the default, so an
            // application that embedded the starter without adding one must still be able to
            // crawl. It gets plain http, which is what it would have had anyway -- said out loud,
            // because a page that needed rendering will now be stored as the shell it arrived as.
            log.warn("No browser engine on the classpath, so 'adaptive' is plain http and pages"
                    + " that render themselves will be stored empty."
                    + " Add htmlunit, playwright or selenium to enable rendering.");
            return engineOf(WebCrawlerConstants.ENGINE_RESTCLIENT);
        }
        // The one that was asked for, then whatever else is on the classpath. Being on the
        // classpath is not the same as being able to start: playwright needs a browser installed,
        // and where there is none htmlunit renders in the jvm rather than nothing rendering at all.
        List<AdaptiveExtractor.Choice> choices = new ArrayList<>();
        choices.add(new AdaptiveExtractor.Choice(browser, () -> engineOf(browser)));
        for (String candidate : WebCrawlerConstants.BROWSER_FALLBACK_ORDER) {
            if (!candidate.equals(browser) && isAvailable(candidate)) {
                choices.add(new AdaptiveExtractor.Choice(candidate, () -> engineOf(candidate)));
            }
        }
        return new AdaptiveExtractor(engineOf(WebCrawlerConstants.ENGINE_RESTCLIENT), choices,
                new RenderingDetector(config.getMinTextLength(), config.getShellTextLength()));
    }

    @Override
    public ExistingUrlPathFilter getExistingUrlPathFilter(CatalogDetails catalogDetails) {
        String filterType = catalogDetails.getUrlPathFilter().toLowerCase();
        WebCrawlerProperties.Dedup.Url config = webCrawlerProperties.getDedup().getUrl();
        String directory = scoped(config.getDirectory(), catalogDetails);
        return switch (filterType) {
            case WebCrawlerConstants.URL_PATH_FILTER_ROCKSDB ->
                new RocksDbUrlPathFilter(directory, config.isNormalize());
            // One built in, and the switch stays: an application that wants another kind of dedup
            // supplies its own WebCrawlerComponentFactory, which is a @ConditionalOnMissingBean
            // bean, and answers this question however it likes.
            default -> throw new UnsupportedOperationException("Unknown UrlPathFilter type: "
                    + filterType + ". The built-in one is "
                    + WebCrawlerConstants.URL_PATH_FILTER_ROCKSDB
                    + "; anything else comes from a WebCrawlerComponentFactory of your own.");
        };
    }

    @Override
    public ContentDedupFilter getContentDedupFilter(CatalogDetails catalogDetails) {
        WebCrawlerProperties.Dedup.Content config = webCrawlerProperties.getDedup().getContent();
        if (!config.isEnabled()) {
            return new ContentDedupFilter.NoOp();
        }
        String directory = scoped(config.getDirectory(), catalogDetails);
        return switch (config.getType().toLowerCase()) {
            case "simhash" -> new SimHashContentDedupFilter(directory, config.getSimhashDistance(),
                    config.getMinTextLength());
            case "sha256" -> new Sha256ContentDedupFilter(directory, config.getMinTextLength());
            default -> throw new UnsupportedOperationException(
                    "Unknown ContentDedupFilter type: " + config.getType());
        };
    }

    @Override
    public GlobalStateManager getGlobalStateManager(CatalogDetails catalogDetails,
            boolean initiator) {
        return new DefaultGlobalStateManager(catalogDetails);
    }

    @Override
    public CrawlFrontier getCrawlFrontier(CatalogDetails catalogDetails) {
        return new RocksDbCrawlFrontier(
                scoped(webCrawlerProperties.getFrontierDirectory(), catalogDetails));
    }

    /**
     * Every RocksDB store is scoped to a catalog and a version, so re-crawling at a new version
     * starts from an empty dedup state while the previous version's data stays intact.
     */
    private String scoped(String baseDirectory, CatalogDetails catalogDetails) {
        return new File(baseDirectory,
                catalogDetails.getId() + File.separator + "v" + catalogDetails.getVersion())
                        .getPath();
    }

}
