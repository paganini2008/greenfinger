package com.github.greenfinger.test;

import java.nio.charset.Charset;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import com.github.doodler.common.events.EventSubscriber;
import com.github.doodler.common.transmitter.NioClient;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.transmitter.Partitioner;
import com.github.doodler.common.utils.CharsetUtils;
import com.github.doodler.common.utils.MapUtils;
import com.github.greenfinger.ResourceManager;
import com.github.greenfinger.WebCrawlerExecutionContext;
import com.github.greenfinger.es.ResourceIndexService;
import com.github.greenfinger.model.Catalog;
import com.github.greenfinger.model.Resource;
import com.github.greenfinger.utils.ExistingUrlPathFilter;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: CrawlerHandler
 * @Author: Fred Feng
 * @Date: 10/01/2025
 * @Version 1.0.0
 */
@Slf4j
public class CrawlerHandler implements EventSubscriber<Packet> {

    private static final String UNIQUE_PATH_IDENTIFIER = "%s||%s||%s||%s";

    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private NioClient nioClient;

    @Autowired
    private Partitioner partitioner;

    @Autowired
    private ResourceIndexService indexService;

    private final Map<Long, Catalog> catalogCache = new ConcurrentHashMap<Long, Catalog>();

    public void consume(Packet packet) {
        final String action = (String) packet.getField("action");
        if (StringUtils.isNotBlank(action)) {
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
            }
        }
    }

    private void doCrawl(Packet packet) {
        final long catalogId = (Long) packet.getField("catalogId");
        WebCrawlerExecutionContext executionContext = WebCrawlerExecutionContext.get(catalogId);
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

        executionContext.getDashboardData().incrementTotalUrlCount();

        ExistingUrlPathFilter urlPathFilter = executionContext.getExistingUrlPathFilter();
        String pathIdentifier =
                String.format(UNIQUE_PATH_IDENTIFIER, catalogId, refer, path, version);
        if (urlPathFilter.mightExist(pathIdentifier)) {
            executionContext.getDashboardData().incrementExistingUrlCount();
            return;
        }
        if (log.isTraceEnabled()) {
            log.trace("Handle resource: [Resource] refer: {}, path: {}", refer, path);
        }

        Charset charset = CharsetUtils.toCharset(pageEncoding);
        String html = null;
        try {
            html = executionContext.getPageSourceExtractor().extractHtml(refer, path, charset,
                    packet);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            executionContext.getDashboardData().incrementInvalidUrlCount();
            html = executionContext.getPageSourceExtractor().defaultPage(refer, path, charset,
                    packet, e);
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

        Resource resource = new Resource();
        resource.setTitle(document.title());
        resource.setHtml(document.html());
        resource.setUrl(path);
        resource.setCat(cat);
        resource.setVersion(version);
        resource.setCatalogId(catalogId);
        resource.setCreateTime(new Date());
        resourceManager.saveResource(resource);
        executionContext.getDashboardData().incrementSavedResourceCount();
        if (log.isTraceEnabled()) {
            log.trace("Save resource: " + resource);
        }
        if (indexEnabled) {
            sendIndex(catalogId, resource.getId(), version);
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
        WebCrawlerExecutionContext executionContext = WebCrawlerExecutionContext.get(catalogId);
        if (executionContext.isCompleted()) {
            return;
        }

        final String action = (String) packet.getField("action");
        final String refer = (String) packet.getField("refer");
        final String path = (String) packet.getField("path");
        final String cat = (String) packet.getField("cat");
        final String pageEncoding = (String) packet.getField("pageEncoding");
        final int version = (Integer) packet.getField("version");
        final boolean indexEnabled = (Boolean) packet.getField("indexEnabled");

        executionContext.getDashboardData().incrementTotalUrlCount();

        Charset charset = CharsetUtils.toCharset(pageEncoding);
        String html = null;
        try {
            html = executionContext.getPageSourceExtractor().extractHtml(refer, path, charset,
                    packet);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            executionContext.getDashboardData().incrementInvalidUrlCount();
            html = executionContext.getPageSourceExtractor().defaultPage(refer, path, charset,
                    packet, e);
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
            log.trace("Handle resource: [Resource] refer: {}, path: {}", refer, path);
        }
        String pathIdentifier =
                String.format(UNIQUE_PATH_IDENTIFIER, catalogId, refer, path, version);
        if (executionContext.getExistingUrlPathFilter().mightExist(pathIdentifier)) {
            executionContext.getDashboardData().incrementExistingUrlCount();
        } else {
            Resource resource = new Resource();
            resource.setTitle(document.title());
            resource.setHtml(document.html());
            resource.setUrl(path);
            resource.setCat(cat);
            resource.setVersion(version);
            resource.setCatalogId(catalogId);
            resource.setCreateTime(new Date());
            resourceManager.saveResource(resource);
            executionContext.getDashboardData().incrementSavedResourceCount();
            if (log.isTraceEnabled()) {
                log.trace("Save resource: " + resource);
            }
            if (indexEnabled) {
                sendIndex(catalogId, resource.getId(), version);
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
                    updateRecursively(action, catalogId, refer, href, version, packet);
                }
            }
        }
    }

    private void doIndex(Packet packet) {
        long catalogId = (Long) packet.getField("catalogId");
        WebCrawlerExecutionContext context = WebCrawlerExecutionContext.get(catalogId);
        Catalog catalog = MapUtils.getOrCreate(catalogCache, catalogId, () -> {
            return resourceManager.getCatalog(catalogId);
        });
        long resourceId = (Long) packet.getField("resourceId");
        int version = (Integer) packet.getField("version");
        Resource resource = resourceManager.getResource(resourceId);
        indexService.indexResource(catalog, resource, false, version);
        context.getDashboardData().incrementIndexedResourceCount();
    }

    private boolean isUrlAcceptable(long catalogId, String refer, String path, Packet packet,
            WebCrawlerExecutionContext context) {
        boolean accepted = context.isUrlAcceptable(refer, path, packet);
        if (!accepted) {
            context.getDashboardData().incrementFilteredUrlCount();
        }
        return accepted;
    }

    private void updateRecursively(String action, long catalogId, String refer, String path,
            int version, Packet current) {
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
        packet.setField("pageEncoding", current.getField("pageEncoding"));
        nioClient.send(packet, partitioner);
    }

    private void crawlRecursively(String action, long catalogId, String refer, String path,
            int version, Packet current, WebCrawlerExecutionContext context) {
        String pathIdentifier =
                String.format(UNIQUE_PATH_IDENTIFIER, catalogId, refer, path, version);
        if (context.getExistingUrlPathFilter().mightExist(pathIdentifier)) {
            context.getDashboardData().incrementExistingUrlCount();
            return;
        }
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
