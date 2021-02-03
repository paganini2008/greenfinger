package org.springtribe.framework.greenfinger;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springtribe.framework.gearless.common.NioClient;
import org.springtribe.framework.gearless.common.Partitioner;
import org.springtribe.framework.gearless.common.Tuple;
import org.springtribe.framework.greenfinger.model.Catalog;
import org.springtribe.framework.greenfinger.model.CatalogIndex;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * CrawlerLauncher
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
@Slf4j
public final class CrawlerLauncher {

	@Autowired
	private NioClient nioClient;

	@Autowired
	private Partitioner partitioner;

	@Autowired
	private ResourceManager resourceManager;

	@Autowired
	private PathFilterFactory pathFilterFactory;

	@Autowired
	private Condition condition;

	@Value("${webcrawler.indexer.enabled:true}")
	private boolean indexEnabled;

	public void rebuild(long catalogId) {
		pathFilterFactory.clean(catalogId);
		submit(catalogId);
	}

	public void submit(long catalogId) {

		condition.reset(catalogId);

		Catalog catalog = resourceManager.getCatalog(catalogId);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("action", "crawl");
		data.put("catalogId", catalog.getId());
		data.put("refer", catalog.getUrl());
		data.put("path", catalog.getUrl());
		data.put("cat", catalog.getCat());
		data.put("pageEncoding", catalog.getPageEncoding());
		data.put("maxFetchSize", catalog.getMaxFetchSize());
		data.put("duration", catalog.getDuration());
		data.put("version", indexEnabled ? getIndexVersion(catalogId) : 0);
		log.info("Catalog Config: {}", data);

		nioClient.send(Tuple.wrap(data), partitioner);
	}

	public void update(long catalogId) {

		condition.reset(catalogId);

		Catalog catalog = resourceManager.getCatalog(catalogId);
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("action", "update");
		data.put("catalogId", catalog.getId());
		data.put("refer", catalog.getUrl());
		data.put("path", getLatestPath(catalog.getId()));
		data.put("cat", catalog.getCat());
		data.put("pageEncoding", catalog.getPageEncoding());
		data.put("maxFetchSize", catalog.getMaxFetchSize());
		data.put("duration", catalog.getDuration());
		data.put("version", indexEnabled ? getIndexVersion(catalogId) : 0);
		log.info("Catalog Config: {}", data);

		nioClient.send(Tuple.wrap(data), partitioner);
	}

	private int getIndexVersion(long catalogId) {
		CatalogIndex catalogIndex = resourceManager.getCatalogIndex(catalogId);
		return catalogIndex.getVersion();
	}

	private String getLatestPath(long catalogId) {
		return resourceManager.getLatestPath(catalogId);
	}
}
