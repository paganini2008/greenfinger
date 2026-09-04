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

package com.github.greenfinger.core.component.extractor;

import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import com.github.greenfinger.core.ManagedBeanLifeCycle;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.engine.CrawlTask;
import com.github.greenfinger.core.utils.BeanLifeCycleUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Fetches cheaply, and pays for a browser only when the cheap fetch came back empty.
 *
 * <p>
 * Configuring a browser engine for a whole catalog is the blunt alternative: correct, and an order
 * of magnitude slower on the great majority of pages that never needed one. This tries plain http
 * first, looks at what came back, and re-fetches through the configured browser when the html looks
 * like an unrendered shell.
 *
 * <p>
 * The browser is created on first use and not before, so a site that turns out to be entirely
 * static never starts one. Which browser it is -- HtmlUnit, Playwright or Selenium -- is the
 * caller's choice; this class only decides <em>when</em>.
 *
 * <p>
 * It keeps count of how often it was right. A crawl that renders nearly every page is a crawl that
 * should have been configured with the browser engine outright, and the numbers in the log are what
 * say so.
 * 
 * @Description: AdaptiveExtractor
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
public class AdaptiveExtractor implements NamedExtractor, ManagedBeanLifeCycle {

    private final Extractor fast;
    private final Supplier<Extractor> browserSupplier;
    private final RenderingDetector detector;
    private final String browserName;

    private volatile Extractor browser;

    @Getter
    private final AtomicLong fetched = new AtomicLong();

    @Getter
    private final AtomicLong rendered = new AtomicLong();

    public AdaptiveExtractor(Extractor fast, String browserName,
            Supplier<Extractor> browserSupplier, RenderingDetector detector) {
        this.fast = fast;
        this.browserName = browserName;
        this.browserSupplier = browserSupplier;
        this.detector = detector;
    }

    @Override
    public String getName() {
        return "adaptive(" + browserName + ")";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        BeanLifeCycleUtils.afterPropertiesSet(fast);
    }

    @Override
    public String extractHtml(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task) throws Exception {
        String html = fast.extractHtml(catalogDetails, referUrl, url, pageEncoding, task);
        fetched.incrementAndGet();

        if (!detector.needsRendering(html)) {
            return html;
        }
        try {
            String renderedHtml = browser().extractHtml(catalogDetails, referUrl, url,
                    pageEncoding, task);
            // only count it when the browser actually produced more than the plain fetch did;
            // otherwise the page really is that empty and the detector was wrong
            if (StringUtils.length(renderedHtml) > StringUtils.length(html)) {
                rendered.incrementAndGet();
                return renderedHtml;
            }
            return html;
        } catch (Exception e) {
            // the browser is the fallback, not the authority: what http returned is still a page
            log.warn("Rendering '{}' with {} failed, keeping the plain fetch: {}", url,
                    browserName, e.getMessage());
            return html;
        }
    }

    /**
     * The conditional question goes to the http engine, which is the one that can ask it. A 304
     * ends the fetch there: the site has said the page is unchanged, so there is nothing for a
     * browser to render even if the page would normally need one.
     */
    @Override
    public FetchedPage fetch(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, CrawlTask task, ConditionalGet conditions) throws Exception {
        FetchedPage plain =
                fast.fetch(catalogDetails, referUrl, url, pageEncoding, task, conditions);
        fetched.incrementAndGet();
        if (plain.notModified()) {
            return plain;
        }
        if (!detector.needsRendering(plain.html())) {
            return plain;
        }
        try {
            String renderedHtml =
                    browser().extractHtml(catalogDetails, referUrl, url, pageEncoding, task);
            if (StringUtils.length(renderedHtml) > StringUtils.length(plain.html())) {
                rendered.incrementAndGet();
                // the validators still come from the http response: they describe the resource the
                // site served, and the browser fetched the same one
                return new FetchedPage(renderedHtml, false, plain.etag(), plain.lastModified());
            }
            return plain;
        } catch (Exception e) {
            log.warn("Rendering '{}' with {} failed, keeping the plain fetch: {}", url, browserName,
                    e.getMessage());
            return plain;
        }
    }

    /**
     * Started on first use. A site that turns out to be static never pays for one at all.
     */
    private Extractor browser() throws Exception {
        Extractor instance = browser;
        if (instance == null) {
            synchronized (this) {
                instance = browser;
                if (instance == null) {
                    log.info("A page needed rendering; starting {}", browserName);
                    instance = browserSupplier.get();
                    BeanLifeCycleUtils.afterPropertiesSet(instance);
                    browser = instance;
                }
            }
        }
        return instance;
    }

    @Override
    public void destroy() {
        BeanLifeCycleUtils.destroyQuietly(fast);
        BeanLifeCycleUtils.destroyQuietly(browser);
        long total = fetched.get();
        long browserPages = rendered.get();
        if (total > 0 && browserPages > 0) {
            log.info("Rendered {} of {} page(s) ({}%) with {}", browserPages, total,
                    browserPages * 100 / total, browserName);
            if (browserPages * 2 > total) {
                log.info("More than half this site needed rendering; setting extractor={} "
                        + "outright would be faster than deciding page by page", browserName);
            }
        }
    }

}
