package com.github.greenfinger;

import java.nio.charset.Charset;
import java.util.Date;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.github.doodler.common.events.Context;
import com.github.doodler.common.events.EventSubscriber;
import com.github.doodler.common.transmitter.Acknowledger;
import com.github.doodler.common.transmitter.NioClient;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.transmitter.Partitioner;
import com.github.doodler.common.utils.CharsetUtils;
import com.github.greenfinger.components.CountingType;
import com.github.greenfinger.components.ExistingUrlPathFilter;
import com.github.greenfinger.model.Resource;
import com.github.greenfinger.searcher.ResourceIndexService;
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
public class WebCrawlerHandler implements EventSubscriber<Packet> {

    private static final String UNIQUE_PATH_IDENTIFIER = "%s||%s||%s||%s";

    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private NioClient nioClient;

    @Autowired
    private Partitioner partitioner;

    @Autowired
    private ResourceIndexService resourceIndexService;

    @Autowired
    private ObjectProvider<Acknowledger> acknowledger;

    @Override
    public void consume(Packet packet, Context context) {
        long catalogId = (Long) packet.getField("catalogId");
        WebCrawlerExecutionContext executionContext =
                WebCrawlerExecutionContextUtils.get(catalogId);
        executionContext.getConcurrents().incrementAndGet();

        String action = (String) packet.getField("action");
        switch (action) {
            case "crawl":
                doCrawl(packet);
                break;
            case "update":
                doUpdate(packet);
                break;
            case "index":
                doIndex(packet);
                break;
            default:
                throw new IllegalArgumentException("Unknown packet: " + packet);
        }
        acknowledger.getObject().succeed(packet);
    }

    @Override
    public void onError(Packet packet, Throwable e, Context context) {
        acknowledger.getObject().backfill(packet);
        if (log.isErrorEnabled()) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void onComplete(Packet packet, Context context) {
        long catalogId = (Long) packet.getField("catalogId");
        WebCrawlerExecutionContext executionContext =
                WebCrawlerExecutionContextUtils.get(catalogId);
        executionContext.getConcurrents().decrementAndGet();
    }

    private void doCrawl(Packet packet) {
        final long catalogId = (Long) packet.getField("catalogId");
        WebCrawlerExecutionContext executionContext =
                WebCrawlerExecutionContextUtils.get(catalogId);
        if (executionContext.isCompleted()) {
            return;
        }

        final String action = (String) packet.getField("action");
        final String refer = (String) packet.getField("refer");
        final String path = (String) packet.getField("path");
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
        if (log.isTraceEnabled()) {
            log.trace("Handling resource: [Resource] refer: {}, path: {}", refer, path);
        }

        Charset charset = CharsetUtils.toCharset(pageEncoding);
        String html = null;
        try {
            html = executionContext.getExtractor().extractHtml(executionContext.getCatalogDetails(),
                    refer, path, charset, packet);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            executionContext.getGlobalStateManager().incrementCount(packet.getTimestamp(),
                    CountingType.INVALID_URL_COUNT);
            html = executionContext.getExtractor().defaultHtml(executionContext.getCatalogDetails(),
                    refer, path, charset, packet, e);
        }
        if (StringUtils.isBlank(html)) {
            log.warn("Get empty html content by path: {}", path);
            return;
        }
        Document document;
        try {
            document = Jsoup.parse(html);
        } catch (Exception ignored) {
            log.warn("Unable to parse html content by path: {}", path);
            return;
        }

        try {
            Resource resource = new Resource();
            resource.setTitle(document.title());
            resource.setHtml(document.html());
            resource.setUrl(path);
            resource.setCat(cat);
            resource.setVersion(version);
            resource.setCatalogId(catalogId);
            resource.setCreateTime(new Date());
            resourceManager.saveResource(resource);
            executionContext.getGlobalStateManager().incrementCount(packet.getTimestamp(),
                    CountingType.SAVED_RESOURCE_COUNT);
            if (log.isInfoEnabled()) {
                log.info("Save resource: " + resource);
            }
            if (indexEnabled) {
                sendIndex(catalogId, resource.getId(), version);
            }
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error(e.getMessage(), e);
            }
        }

        Elements elements = document.body().select("a");
        if (CollectionUtils.isNotEmpty(elements)) {
            String href;
            for (Element element : elements) {
                href = element.absUrl("href");
                if (StringUtils.isBlank(href)) {
                    href = element.attr("href");
                }
                if (StringUtils.isNotBlank(href)
                        && isUrlAcceptable(catalogId, refer, href, packet, executionContext)) {
                    crawlRecursively(action, catalogId, refer, href, version, packet,
                            executionContext);
                }
            }
        }
    }

    private void doUpdate(Packet packet) {
        final long catalogId = (Long) packet.getField("catalogId");
        WebCrawlerExecutionContext executionContext =
                WebCrawlerExecutionContextUtils.get(catalogId);
        if (executionContext.isCompleted()) {
            return;
        }

        final String action = (String) packet.getField("action");
        final String refer = (String) packet.getField("refer");
        final String path = (String) packet.getField("path");
        final String cat = (String) packet.getField("cat");
        final String pageEncoding = (String) packet.getField("pageEncoding");
        final int version = (Integer) packet.getField("version");
        final boolean indexEnabled = (Boolean) packet.getField("indexEnabled", true);

        executionContext.getGlobalStateManager().incrementCount(packet.getTimestamp(),
                CountingType.URL_TOTAL_COUNT);

        Charset charset = CharsetUtils.toCharset(pageEncoding);
        String html = null;
        try {
            html = executionContext.getExtractor().extractHtml(executionContext.getCatalogDetails(),
                    refer, path, charset, packet);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            executionContext.getGlobalStateManager().incrementCount(packet.getTimestamp(),
                    CountingType.INVALID_URL_COUNT);
            html = executionContext.getExtractor().defaultHtml(executionContext.getCatalogDetails(),
                    refer, path, charset, packet, e);
        }
        if (StringUtils.isBlank(html)) {
            return;
        }

        Document document;
        try {
            document = Jsoup.parse(html);
        } catch (Exception ignored) {
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("Handling resource: [Resource] refer: {}, path: {}", refer, path);
        }
        String pathIdentifier =
                String.format(UNIQUE_PATH_IDENTIFIER, catalogId, refer, path, version);
        if (executionContext.getExistingUrlPathFilter().mightExist(pathIdentifier)) {
            executionContext.getGlobalStateManager().incrementCount(packet.getTimestamp(),
                    CountingType.EXISTING_URL_COUNT);
        } else {

            try {
                Resource resource = new Resource();
                resource.setTitle(document.title());
                resource.setHtml(document.html());
                resource.setUrl(path);
                resource.setCat(cat);
                resource.setVersion(version);
                resource.setCatalogId(catalogId);
                resource.setCreateTime(new Date());
                resourceManager.saveResource(resource);
                executionContext.getGlobalStateManager().incrementCount(packet.getTimestamp(),
                        CountingType.SAVED_RESOURCE_COUNT);
                if (log.isInfoEnabled()) {
                    log.info("Save resource: " + resource);
                }
                if (indexEnabled) {
                    sendIndex(catalogId, resource.getId(), version);
                }
            } catch (Exception e) {
                if (log.isErrorEnabled()) {
                    log.error(e.getMessage(), e);
                }
            }
        }

        Elements elements = document.body().select("a");
        if (CollectionUtils.isNotEmpty(elements)) {
            String href;
            for (Element element : elements) {
                href = element.absUrl("href");
                if (StringUtils.isBlank(href)) {
                    href = element.attr("href");
                }
                if (StringUtils.isNotBlank(href)
                        && isUrlAcceptable(catalogId, refer, href, packet, executionContext)) {
                    updateRecursively(action, catalogId, refer, href, version, packet,
                            executionContext);
                }
            }
        }
    }

    private void doIndex(Packet packet) {
        long catalogId = (Long) packet.getField("catalogId");
        WebCrawlerExecutionContext context = WebCrawlerExecutionContextUtils.get(catalogId);
        long resourceId = (Long) packet.getField("resourceId");
        int version = (Integer) packet.getField("version");
        Resource resource = resourceManager.getResource(resourceId);
        resourceIndexService.indexResource(context.getCatalogDetails(), resource, version);
        context.getGlobalStateManager().incrementCount(packet.getTimestamp(),
                CountingType.INDEXED_RESOURCE_COUNT);
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
        // String pathIdentifier =
        // String.format(UNIQUE_PATH_IDENTIFIER, catalogId, refer, path, version);
        // if (context.getExistingUrlPathFilter().mightExist(pathIdentifier)) {
        // context.getGlobalStateManager().incrementCount(CountingType.EXISTING_URL_COUNT);
        // return;
        // }
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

}
