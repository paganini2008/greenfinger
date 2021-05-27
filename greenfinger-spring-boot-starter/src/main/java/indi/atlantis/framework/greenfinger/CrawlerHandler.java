package indi.atlantis.framework.greenfinger;

import java.nio.charset.Charset;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.github.paganini2008.devtools.CharsetUtils;
import com.github.paganini2008.devtools.StringUtils;
import com.github.paganini2008.devtools.collection.CollectionUtils;
import com.github.paganini2008.devtools.collection.MapUtils;

import indi.atlantis.framework.greenfinger.es.IndexedResourceService;
import indi.atlantis.framework.greenfinger.model.Catalog;
import indi.atlantis.framework.greenfinger.model.Resource;
import indi.atlantis.framework.vortex.Handler;
import indi.atlantis.framework.vortex.common.HashPartitioner;
import indi.atlantis.framework.vortex.common.NioClient;
import indi.atlantis.framework.vortex.common.Partitioner;
import indi.atlantis.framework.vortex.common.Tuple;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * CrawlerHandler
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
@Slf4j
public class CrawlerHandler implements Handler {

	private static final String UNIQUE_PATH_IDENTIFIER = "%s||%s||%s||%s";

	@Autowired
	private PageExtractor pageExtractor;

	@Autowired
	private ResourceManager resourceManager;

	@Autowired
	private NioClient nioClient;

	@Autowired
	private Partitioner partitioner;

	@Autowired
	private PathAcceptor pathAcceptor;

	@Autowired
	private Condition condition;

	@Autowired
	private PathFilterFactory pathFilterFactory;

	@Autowired
	private IndexedResourceService indexService;

	@Autowired
	private CrawlerSummary crawlerSummary;

	@Value("${webcrawler.crawler.fetch.depth:-1}")
	private int depth;

	@Value("${webcrawler.indexer.enabled:true}")
	private boolean indexEnabled;

	private final Map<Long, Catalog> catalogCache = new ConcurrentHashMap<Long, Catalog>();
	private final Map<Long, PathFilter> pathFilters = new ConcurrentHashMap<Long, PathFilter>();

	private PathFilter getPathFilter(long catalogId) {
		return MapUtils.get(pathFilters, catalogId, () -> {
			return pathFilterFactory.getPathFilter(catalogId);
		});
	}

	public void onData(Tuple tuple) {
		final String action = (String) tuple.getField("action");
		if (StringUtils.isNotBlank(action)) {
			switch (action) {
			case "crawl":
				doCrawl(tuple);
				break;
			case "update":
				doUpdate(tuple);
				break;
			case "index":
				doIndex(tuple);
				break;
			}
		}
	}

	private void doCrawl(Tuple tuple) {
		final long catalogId = (Long) tuple.getField("catalogId");
		if (condition.isCompleted(catalogId, tuple)) {
			return;
		}

		final String action = (String) tuple.getField("action");
		final String refer = (String) tuple.getField("refer");
		final String path = (String) tuple.getField("path");
		final String cat = (String) tuple.getField("cat");
		final String pageEncoding = (String) tuple.getField("pageEncoding");
		final int version = (Integer) tuple.getField("version");

		crawlerSummary.getSummary(catalogId).incrementUrlCount();

		PathFilter pathFilter = getPathFilter(catalogId);
		String pathIdentifier = String.format(UNIQUE_PATH_IDENTIFIER, catalogId, refer, path, version);
		if (pathFilter.mightExist(pathIdentifier)) {
			crawlerSummary.getSummary(catalogId).incrementExistedUrlCount();
			return;
		}
		pathFilter.update(pathIdentifier);
		if (log.isTraceEnabled()) {
			log.trace("Handle resource: [Resource] refer: {}, path: {}", refer, path);
		}

		Charset charset = CharsetUtils.toCharset(pageEncoding);
		String html = null;
		try {
			html = pageExtractor.extractHtml(refer, path, charset);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			crawlerSummary.getSummary(catalogId).incrementInvalidUrlCount();
			html = pageExtractor.defaultPage(refer, path, charset, e);
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
		crawlerSummary.getSummary(catalogId).incrementSavedCount();
		if (log.isTraceEnabled()) {
			log.trace("Save resource: " + resource);
		}
		if (indexEnabled) {
			sendIndex(catalogId, resource.getId(), version);
		}

		condition.mightComplete(catalogId, tuple);

		Elements elements = document.body().select("a");
		if (CollectionUtils.isNotEmpty(elements)) {
			String href;
			for (Element element : elements) {
				href = element.absUrl("href");
				if (StringUtils.isBlank(href)) {
					href = element.attr("href");
				}
				if (StringUtils.isNotBlank(href) && acceptedPath(catalogId, refer, href, tuple)) {
					crawlRecursively(action, catalogId, refer, href, version, tuple, pathFilter);
				}
			}
		}
	}

	private void doUpdate(Tuple tuple) {
		final long catalogId = (Long) tuple.getField("catalogId");
		if (condition.isCompleted(catalogId, tuple)) {
			return;
		}

		final String action = (String) tuple.getField("action");
		final String refer = (String) tuple.getField("refer");
		final String path = (String) tuple.getField("path");
		final String cat = (String) tuple.getField("cat");
		final String pageEncoding = (String) tuple.getField("pageEncoding");
		final int version = (Integer) tuple.getField("version");
		crawlerSummary.getSummary(catalogId).incrementUrlCount();

		Charset charset = CharsetUtils.toCharset(pageEncoding);
		String html = null;
		try {
			html = pageExtractor.extractHtml(refer, path, charset);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			crawlerSummary.getSummary(catalogId).incrementInvalidUrlCount();
			html = pageExtractor.defaultPage(refer, path, charset, e);
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
		PathFilter pathFilter = getPathFilter(catalogId);
		String pathIdentifier = String.format(UNIQUE_PATH_IDENTIFIER, catalogId, refer, path, version);
		if (pathFilter.mightExist(pathIdentifier)) {
			crawlerSummary.getSummary(catalogId).incrementExistedUrlCount();
		} else {
			pathFilter.update(pathIdentifier);

			Resource resource = new Resource();
			resource.setTitle(document.title());
			resource.setHtml(document.html());
			resource.setUrl(path);
			resource.setCat(cat);
			resource.setVersion(version);
			resource.setCatalogId(catalogId);
			resource.setCreateTime(new Date());
			resourceManager.saveResource(resource);
			crawlerSummary.getSummary(catalogId).incrementSavedCount();
			if (log.isTraceEnabled()) {
				log.trace("Save resource: " + resource);
			}
			if (indexEnabled) {
				sendIndex(catalogId, resource.getId(), version);
			}
		}

		condition.mightComplete(catalogId, tuple);

		Elements elements = document.body().select("a");
		if (CollectionUtils.isNotEmpty(elements)) {
			String href;
			for (Element element : elements) {
				href = element.absUrl("href");
				if (StringUtils.isBlank(href)) {
					href = element.attr("href");
				}
				if (StringUtils.isNotBlank(href) && acceptedPath(catalogId, refer, href, tuple)) {
					updateRecursively(action, catalogId, refer, href, version, tuple);
				}
			}
		}
	}

	private void doIndex(Tuple tuple) {
		long catalogId = (Long) tuple.getField("catalogId");
		Catalog catalog = MapUtils.get(catalogCache, catalogId, () -> {
			return resourceManager.getCatalog(catalogId);
		});
		long resourceId = (Long) tuple.getField("resourceId");
		int version = (Integer) tuple.getField("version");
		Resource resource = resourceManager.getResource(resourceId);
		indexService.index(catalog, resource, false, version);
		crawlerSummary.getSummary(catalogId).incrementIndexedCount();
	}

	private boolean acceptedPath(long catalogId, String refer, String path, Tuple tuple) {
		boolean accepted = true;
		if (!pathAcceptor.accept(catalogId, refer, path, tuple)) {
			accepted = false;
		}
		if (!testDepth(refer, path)) {
			accepted = false;
		}
		if (!accepted) {
			crawlerSummary.getSummary(catalogId).incrementFilteredUrlCount();
		}
		return accepted;
	}

	private boolean testDepth(String refer, String path) {
		if (depth < 0) {
			return true;
		}
		String part = path.replace(refer, "");
		if (part.charAt(0) == '/') {
			part = part.substring(1);
		}
		int n = 0;
		for (char ch : part.toCharArray()) {
			if (ch == '/') {
				n++;
			}
		}
		return n <= depth;
	}

	private void updateRecursively(String action, long catalogId, String refer, String path, int version, Tuple current) {
		Tuple tuple = Tuple.newOne();
		tuple.setField(Tuple.PARTITIONER_NAME, HashPartitioner.class.getName());
		tuple.setField("action", action);
		tuple.setField("catalogId", catalogId);
		tuple.setField("refer", refer);
		tuple.setField("path", path);
		tuple.setField("version", version);
		tuple.setField("cat", current.getField("cat"));
		tuple.setField("duration", current.getField("duration"));
		tuple.setField("maxFetchSize", current.getField("maxFetchSize"));
		tuple.setField("pageEncoding", current.getField("pageEncoding"));
		nioClient.send(tuple, partitioner);
	}

	private void crawlRecursively(String action, long catalogId, String refer, String path, int version, Tuple current,
			PathFilter pathFilter) {
		String pathIdentifier = String.format(UNIQUE_PATH_IDENTIFIER, catalogId, refer, path, version);
		if (pathFilter.mightExist(pathIdentifier)) {
			crawlerSummary.getSummary(catalogId).incrementExistedUrlCount();
			return;
		}
		Tuple tuple = Tuple.newOne();
		tuple.setField(Tuple.PARTITIONER_NAME, HashPartitioner.class.getName());
		tuple.setField("action", action);
		tuple.setField("catalogId", catalogId);
		tuple.setField("refer", refer);
		tuple.setField("path", path);
		tuple.setField("version", version);
		tuple.setField("cat", current.getField("cat"));
		tuple.setField("duration", current.getField("duration"));
		tuple.setField("maxFetchSize", current.getField("maxFetchSize"));
		tuple.setField("pageEncoding", current.getField("pageEncoding"));
		nioClient.send(tuple, partitioner);
	}

	private void sendIndex(long catalogId, long resourceId, int version) {
		Tuple tuple = Tuple.newOne();
		tuple.setField("action", "index");
		tuple.setField("catalogId", catalogId);
		tuple.setField("resourceId", resourceId);
		tuple.setField("version", version);
		nioClient.send(tuple, partitioner);
	}

}
