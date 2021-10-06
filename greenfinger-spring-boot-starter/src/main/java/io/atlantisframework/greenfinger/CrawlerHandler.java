/**
* Copyright 2017-2021 Fred Feng (paganini.fy@gmail.com)

* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package io.atlantisframework.greenfinger;

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

import io.atlantisframework.greenfinger.es.ResourceIndexService;
import io.atlantisframework.greenfinger.model.Catalog;
import io.atlantisframework.greenfinger.model.Resource;
import io.atlantisframework.vortex.Handler;
import io.atlantisframework.vortex.common.HashPartitioner;
import io.atlantisframework.vortex.common.NioClient;
import io.atlantisframework.vortex.common.Partitioner;
import io.atlantisframework.vortex.common.Tuple;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * CrawlerHandler
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
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
	private PathAcceptorContainer pathAcceptorContainer;

	@Autowired
	private Condition condition;

	@Autowired
	private PathFilterFactory pathFilterFactory;

	@Autowired
	private ResourceIndexService indexService;

	@Autowired
	private CrawlerStatistics crawlerStatistics;

	@Value("${atlantis.framework.greenfinger.crawler.fetchDepth:-1}")
	private int defaultFetchDepth;

	@Value("${atlantis.framework.greenfinger.indexer.enabled:true}")
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

		crawlerStatistics.getSummary(catalogId).incrementUrlCount();

		PathFilter pathFilter = getPathFilter(catalogId);
		String pathIdentifier = String.format(UNIQUE_PATH_IDENTIFIER, catalogId, refer, path, version);
		if (pathFilter.mightExist(pathIdentifier)) {
			crawlerStatistics.getSummary(catalogId).incrementExistedUrlCount();
			return;
		}
		pathFilter.update(pathIdentifier);
		if (log.isTraceEnabled()) {
			log.trace("Handle resource: [Resource] refer: {}, path: {}", refer, path);
		}

		Charset charset = CharsetUtils.toCharset(pageEncoding);
		String html = null;
		try {
			html = pageExtractor.extractHtml(refer, path, charset, tuple);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			crawlerStatistics.getSummary(catalogId).incrementInvalidUrlCount();
			html = pageExtractor.defaultPage(refer, path, charset, tuple, e);
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
		crawlerStatistics.getSummary(catalogId).incrementSavedCount();
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

		crawlerStatistics.getSummary(catalogId).incrementUrlCount();

		Charset charset = CharsetUtils.toCharset(pageEncoding);
		String html = null;
		try {
			html = pageExtractor.extractHtml(refer, path, charset, tuple);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			crawlerStatistics.getSummary(catalogId).incrementInvalidUrlCount();
			html = pageExtractor.defaultPage(refer, path, charset, tuple, e);
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
			crawlerStatistics.getSummary(catalogId).incrementExistedUrlCount();
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
			crawlerStatistics.getSummary(catalogId).incrementSavedCount();
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
		indexService.indexResource(catalog, resource, false, version);
		crawlerStatistics.getSummary(catalogId).incrementIndexedCount();
	}

	private boolean acceptedPath(long catalogId, String refer, String path, Tuple tuple) {
		boolean accepted = true;
		if (!pathAcceptorContainer.acceptAll(catalogId, refer, path, tuple)) {
			accepted = false;
		}
		if (!testFetchDepth(refer, path, tuple)) {
			accepted = false;
		}
		if (!accepted) {
			crawlerStatistics.getSummary(catalogId).incrementFilteredUrlCount();
		}
		return accepted;
	}

	private boolean testFetchDepth(String refer, String path, Tuple tuple) {
		int depth = (Integer) tuple.getField("depth", defaultFetchDepth);
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
			crawlerStatistics.getSummary(catalogId).incrementExistedUrlCount();
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