package com.github.greenfinger;

import java.nio.charset.Charset;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import com.github.doodler.common.context.ManagedBeanLifeCycle;
import com.github.doodler.common.events.Context;
import com.github.doodler.common.events.EventSubscriber;
import com.github.doodler.common.transmitter.NioClient;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.transmitter.PacketRetryer;
import com.github.doodler.common.transmitter.Partitioner;
import com.github.doodler.common.transmitter.PerformanceInspector;
import com.github.doodler.common.utils.CharsetUtils;
import com.github.doodler.common.utils.ExecutorUtils;
import com.github.greenfinger.components.CountingType;
import com.github.greenfinger.components.ExistingUrlPathFilter;
import com.github.greenfinger.model.Resource;
import com.github.greenfinger.searcher.ResourceIndexManager;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: WebCrawlerHandler
 * @Author: Fred Feng
 * @Date: 10/01/2025
 * @Version 1.0.0
 */
@Slf4j
@Component
public class WebCrawlerHandler implements EventSubscriber<Packet>, ManagedBeanLifeCycle {

    private static final String UNIQUE_PATH_IDENTIFIER = "%s||%s||%s||%s";

    @Autowired
    private WebCrawlerProperties webCrawlerProperties;

    @Autowired
    private WebCrawlerExtractorProperties extractorProperties;

    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private NioClient nioClient;

    @Autowired
    private Partitioner partitioner;

    @Autowired
    private ResourceIndexManager resourceIndexManager;

    @Lazy
    @Autowired
    private PacketRetryer packetRetryer;

    @Lazy
    @Autowired
    private PerformanceInspector performanceInspector;

    private ExecutorService workThreads;

    @Override
    public void consume(Packet packet, Context context) {
        performanceInspector.beforeConsuming(packet);
        final String action = (String) packet.getField("action");
        switch (action) {
            case "crawl":
                doCrawl(packet, context);
                break;
            case "update":
                doUpdate(packet, context);
                break;
            case "index":
                doIndex(packet, context);
                break;
            default:
                throw new IllegalArgumentException("Unknown packet: " + packet);
        }
    }

    private void doCrawl(Packet packet, Context context) {
        final long catalogId = (Long) packet.getField("catalogId");
        WebCrawlerExecutionContext executionContext =
                WebCrawlerExecutionContextUtils.get(catalogId);
        if (executionContext == null || executionContext.isCompleted()) {
            return;
        }

        final String action = (String) packet.getField("action");
        final String refer = (String) packet.getField("refer");
        String path = (String) packet.getField("path");
        if (path.equals(refer + "/")) {
            path = refer;
        }
        final String cat = (String) packet.getField("cat");
        final String pageEncoding = (String) packet.getField("pageEncoding");
        final int version = (Integer) packet.getField("version");
        final boolean indexEnabled = (Boolean) packet.getField("indexEnabled", true);
        executionContext.getGlobalStateManager().incrementCount(packet.getTimestamp(),
                CountingType.URL_TOTAL_COUNT);

        ExistingUrlPathFilter urlPathFilter = executionContext.getExistingUrlPathFilter();
        String pathIdentifier =
                String.format(UNIQUE_PATH_IDENTIFIER, catalogId, refer, path, version);
        if (urlPathFilter.mightExist(pathIdentifier)) {
            executionContext.getGlobalStateManager().incrementCount(packet.getTimestamp(),
                    CountingType.EXISTING_URL_COUNT);
            return;
        }

        executionContext.getConcurrents().incrementAndGet();
        if (log.isTraceEnabled()) {
            log.trace("Handling resource: [Resource] refer: {}, path: {}", refer, path);
        }
        final String thisPath = path;
        CompletableFuture.supplyAsync(() -> {
            Charset charset = CharsetUtils.toCharset(pageEncoding);
            String html = null;
            try {
                html = executionContext.getExtractor().extractHtml(
                        executionContext.getCatalogDetails(), refer, thisPath, charset, packet);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                executionContext.getGlobalStateManager().incrementCount(packet.getTimestamp(),
                        CountingType.INVALID_URL_COUNT);
                html = executionContext.getExtractor().defaultHtml(
                        executionContext.getCatalogDetails(), refer, thisPath, charset, packet, e);
            }
            return html;
        }, workThreads)
                .completeOnTimeout("", extractorProperties.getTimeout(), TimeUnit.MILLISECONDS)
                .thenApply(html -> {
                    if (StringUtils.isBlank(html)) {
                        log.info("No page content with path: {}", thisPath);
                        return null;
                    }
                    Document document;
                    try {
                        document = Jsoup.parse(html);
                        if (StringUtils.isBlank(document.body().text())) {
                            log.trace("No text content on path: {}", thisPath);
                            return null;
                        }
                    } catch (Exception ignored) {
                        log.trace("Unable to parse html content with path: {}", thisPath);
                        return null;
                    }
                    if (log.isTraceEnabled()) {
                        log.trace("Handling resource: [Resource] refer: {}, path: {}", refer,
                                thisPath);
                    }
                    try {
                        Resource resource = new Resource();
                        resource.setTitle(document.title());
                        resource.setHtml(document.html());
                        resource.setUrl(thisPath);
                        resource.setCat(cat);
                        resource.setVersion(version);
                        resource.setCatalogId(catalogId);
                        resource.setCreateTime(new Date());
                        resourceManager.saveResource(resource);
                        executionContext.getGlobalStateManager().incrementCount(
                                packet.getTimestamp(), CountingType.SAVED_RESOURCE_COUNT);
                        if (log.isInfoEnabled()) {
                            log.info("Save resource: " + resource);
                        }
                        if (indexEnabled) {
                            sendIndex(catalogId, resource.getId(), version);
                        }
                    } catch (DuplicateKeyException e) {
                        if (log.isWarnEnabled()) {
                            log.warn(e.getMessage());
                        }
                        executionContext.getGlobalStateManager().incrementCount(
                                packet.getTimestamp(), CountingType.EXISTING_URL_COUNT);
                    } catch (Exception e) {
                        if (log.isErrorEnabled()) {
                            log.error(e.getMessage(), e);
                        }
                    }
                    return document;
                }).thenApply(document -> {
                    if (document == null) {
                        return 0;
                    }
                    int links = 0;
                    if (!executionContext.isCompleted()) {
                        Elements elements = document.body().select("a");
                        if (CollectionUtils.isNotEmpty(elements)) {
                            links = elements.size();
                            String href;
                            for (Element element : elements) {
                                href = element.absUrl("href");
                                if (StringUtils.isBlank(href)) {
                                    href = element.attr("href");
                                }
                                if (StringUtils.isNotBlank(href) && isUrlAcceptable(catalogId,
                                        refer, href, packet, executionContext)) {
                                    crawlRecursively(action, catalogId, refer, href, version,
                                            packet, executionContext);
                                }
                            }
                        }
                    }
                    return links;
                }).whenComplete((result, e) -> {
                    if (e != null) {
                        packetRetryer.backfill(packet);
                        if (log.isErrorEnabled()) {
                            log.error(e.getMessage(), e);
                        }
                    }
                    executionContext.getConcurrents().decrementAndGet();
                    performanceInspector.completeConsuming(packet, e);
                });
    }

    private void doUpdate(Packet packet, Context context) {
        final long catalogId = (Long) packet.getField("catalogId");
        WebCrawlerExecutionContext executionContext =
                WebCrawlerExecutionContextUtils.get(catalogId);
        if (executionContext == null || executionContext.isCompleted()) {
            return;
        }

        final AtomicReference<String> action =
                new AtomicReference<>((String) packet.getField("action"));
        final String refer = (String) packet.getField("refer");
        String path = (String) packet.getField("path");
        if (path.equals(refer + "/")) {
            path = refer;
        }
        final String cat = (String) packet.getField("cat");
        final String pageEncoding = (String) packet.getField("pageEncoding");
        final int version = (Integer) packet.getField("version");
        final boolean indexEnabled = (Boolean) packet.getField("indexEnabled", true);
        executionContext.getGlobalStateManager().incrementCount(packet.getTimestamp(),
                CountingType.URL_TOTAL_COUNT);

        executionContext.getConcurrents().incrementAndGet();
        final String thisPath = path;
        CompletableFuture.supplyAsync(() -> {
            Charset charset = CharsetUtils.toCharset(pageEncoding);
            String html = null;
            try {
                html = executionContext.getExtractor().extractHtml(
                        executionContext.getCatalogDetails(), refer, thisPath, charset, packet);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                executionContext.getGlobalStateManager().incrementCount(packet.getTimestamp(),
                        CountingType.INVALID_URL_COUNT);
                html = executionContext.getExtractor().defaultHtml(
                        executionContext.getCatalogDetails(), refer, thisPath, charset, packet, e);
            }
            return html;
        }, workThreads)
                .completeOnTimeout("", extractorProperties.getTimeout(), TimeUnit.MILLISECONDS)
                .thenApply(html -> {
                    if (StringUtils.isBlank(html)) {
                        log.info("No page content with path: {}", thisPath);
                        return null;
                    }
                    Document document;
                    try {
                        document = Jsoup.parse(html);
                        if (StringUtils.isBlank(document.body().text())) {
                            log.trace("No text content on path: {}", thisPath);
                            return null;
                        }
                    } catch (Exception ignored) {
                        log.trace("Unable to parse html content with path: {}", thisPath);
                        return null;
                    }
                    if (log.isTraceEnabled()) {
                        log.trace("Handling resource: [Resource] refer: {}, path: {}", refer,
                                thisPath);
                    }
                    String pathIdentifier = String.format(UNIQUE_PATH_IDENTIFIER, catalogId, refer,
                            thisPath, version);
                    if (executionContext.getExistingUrlPathFilter().mightExist(pathIdentifier)) {
                        executionContext.getGlobalStateManager().incrementCount(
                                packet.getTimestamp(), CountingType.EXISTING_URL_COUNT);
                    } else {
                        action.set("crawl");
                        try {
                            Resource resource = new Resource();
                            resource.setTitle(document.title());
                            resource.setHtml(document.html());
                            resource.setUrl(thisPath);
                            resource.setCat(cat);
                            resource.setVersion(version);
                            resource.setCatalogId(catalogId);
                            resource.setCreateTime(new Date());
                            resourceManager.saveResource(resource);
                            executionContext.getGlobalStateManager().incrementCount(
                                    packet.getTimestamp(), CountingType.SAVED_RESOURCE_COUNT);
                            if (log.isInfoEnabled()) {
                                log.info("Save resource: " + resource);
                            }
                            if (indexEnabled) {
                                sendIndex(catalogId, resource.getId(), version);
                            }
                        } catch (DuplicateKeyException e) {
                            if (log.isWarnEnabled()) {
                                log.warn(e.getMessage());
                            }
                            executionContext.getGlobalStateManager().incrementCount(
                                    packet.getTimestamp(), CountingType.EXISTING_URL_COUNT);
                        } catch (Exception e) {
                            if (log.isErrorEnabled()) {
                                log.error(e.getMessage(), e);
                            }
                        }
                    }
                    return document;
                }).thenApply(document -> {
                    if (document == null) {
                        return 0;
                    }
                    int links = 0;
                    if (!executionContext.isCompleted()) {
                        Elements elements = document.body().select("a");
                        if (CollectionUtils.isNotEmpty(elements)) {
                            links = elements.size();
                            String href;
                            for (Element element : elements) {
                                href = element.absUrl("href");
                                if (StringUtils.isBlank(href)) {
                                    href = element.attr("href");
                                }
                                if (StringUtils.isNotBlank(href) && isUrlAcceptable(catalogId,
                                        refer, href, packet, executionContext)) {
                                    updateRecursively(action.get(), catalogId, refer, href, version,
                                            packet, executionContext);
                                }
                            }
                        }
                    }
                    return links;
                }).whenComplete((result, e) -> {
                    if (e != null) {
                        packetRetryer.backfill(packet);
                        if (log.isErrorEnabled()) {
                            log.error(e.getMessage(), e);
                        }
                    }
                    executionContext.getConcurrents().decrementAndGet();
                    performanceInspector.completeConsuming(packet, e);
                });
    }

    private void doIndex(Packet packet, Context context) {
        final long catalogId = (Long) packet.getField("catalogId");
        WebCrawlerExecutionContext executionContext =
                WebCrawlerExecutionContextUtils.get(catalogId);
        if (executionContext == null) {
            return;
        }
        executionContext.getConcurrents().incrementAndGet();
        try {
            long resourceId = (Long) packet.getField("resourceId");
            int version = (Integer) packet.getField("version");
            Resource resource = resourceManager.getResource(resourceId);
            resourceIndexManager.indexResource(executionContext.getCatalogDetails(), resource,
                    version);
            executionContext.getGlobalStateManager().incrementCount(packet.getTimestamp(),
                    CountingType.INDEXED_RESOURCE_COUNT);
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error(e.getMessage(), e);
            }
        } finally {
            executionContext.getConcurrents().decrementAndGet();
            performanceInspector.completeConsuming(packet, null);
        }
    }

    private boolean isUrlAcceptable(long catalogId, String refer, String path, Packet packet,
            WebCrawlerExecutionContext context) {
        boolean accepted = context.isUrlAcceptable(refer, path, packet);
        if (!accepted) {
            context.getGlobalStateManager().incrementCount(packet.getTimestamp(),
                    CountingType.FILTERED_URL_COUNT);
        }
        return accepted;
    }

    private void updateRecursively(String action, long catalogId, String refer, String path,
            int version, Packet current, WebCrawlerExecutionContext context) {
        Packet packet = new Packet();
        packet.setField("partitioner", "hash");
        packet.setField("action", action);
        packet.setField("catalogId", catalogId);
        packet.setField("refer", refer);
        packet.setField("path", path);
        packet.setField("version", version);
        packet.setField("cat", current.getField("cat"));
        packet.setField("duration", current.getField("duration"));
        packet.setField("depth", current.getField("depth"));
        packet.setField("interval", current.getField("interval"));
        packet.setField("maxFetchSize", current.getField("maxFetchSize"));
        packet.setField("pageEncoding", current.getField("pageEncoding"));
        nioClient.send(packet, partitioner);
    }

    private void crawlRecursively(String action, long catalogId, String refer, String path,
            int version, Packet current, WebCrawlerExecutionContext context) {

        Packet packet = new Packet();
        packet.setField("partitioner", "hash");
        packet.setField("action", action);
        packet.setField("catalogId", catalogId);
        packet.setField("refer", refer);
        packet.setField("path", path);
        packet.setField("version", version);
        packet.setField("cat", current.getField("cat"));
        packet.setField("duration", current.getField("duration"));
        packet.setField("maxFetchSize", current.getField("maxFetchSize"));
        packet.setField("depth", current.getField("depth"));
        packet.setField("interval", current.getField("interval"));
        packet.setField("pageEncoding", current.getField("pageEncoding"));
        nioClient.send(packet, partitioner);
    }

    private void sendIndex(long catalogId, long resourceId, int version) {
        Packet packet = new Packet();
        packet.setField("action", "index");
        packet.setField("catalogId", catalogId);
        packet.setField("resourceId", resourceId);
        packet.setField("version", version);
        nioClient.send(packet, partitioner);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("WebCrawlerHandler will work with thread count: {}",
                webCrawlerProperties.getWorkThreads());
        ThreadFactory threadFactory =
                new ThreadFactoryBuilder().setNameFormat("WebCrawlerHandler-ThreadPool-%d").build();
        this.workThreads = webCrawlerProperties.getWorkThreads() > 0
                ? Executors.newFixedThreadPool(webCrawlerProperties.getWorkThreads(), threadFactory)
                : Executors.newCachedThreadPool(threadFactory);
    }

    @Override
    public void destroy() throws Exception {
        ExecutorUtils.gracefulShutdown(workThreads, 60000L);
    }

}
