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
package indi.atlantis.framework.greenfinger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.github.paganini2008.devtools.date.DateUtils;

import indi.atlantis.framework.greenfinger.model.Catalog;
import indi.atlantis.framework.greenfinger.model.CatalogIndex;
import indi.atlantis.framework.vortex.common.HashPartitioner;
import indi.atlantis.framework.vortex.common.NioClient;
import indi.atlantis.framework.vortex.common.Partitioner;
import indi.atlantis.framework.vortex.common.Tuple;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * CrawlerLauncher
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
@Slf4j
public final class CrawlerLauncher {

	static final long DEFAULT_CRAWLER_IDLE_TIMEOUT = DateUtils.convertToMillis(5, TimeUnit.MINUTES);

	@Autowired
	private NioClient nioClient;

	@Autowired
	private Partitioner partitioner;

	@Autowired
	private ResourceManager resourceManager;

	@Autowired
	private CatalogAdminService catalogAdminService;

	@Autowired
	private Condition defaultCondition;

	@Value("${atlantis.framework.greenfinger.indexer.enabled:true}")
	private boolean indexEnabled;

	public void rebuild(long catalogId, Condition extraCondition) {
		catalogAdminService.cleanCatalog(catalogId, true);
		resourceManager.incrementCatalogIndexVersion(catalogId);
		submit(catalogId, extraCondition);
	}

	public void submit(long catalogId, Condition extraCondition) {
		Catalog catalog = resourceManager.getCatalog(catalogId);
		Condition condition = extraCondition != null ? extraCondition : defaultCondition;
		condition.reset(catalogId, catalog.getDuration() != null ? catalog.getDuration().longValue() : DEFAULT_CRAWLER_IDLE_TIMEOUT);

		Map<String, Object> data = new HashMap<String, Object>();
		data.put(Tuple.PARTITIONER_NAME, HashPartitioner.class.getName());
		data.put("action", "crawl");
		data.put("catalogId", catalog.getId());
		data.put("refer", catalog.getUrl());
		data.put("path", catalog.getUrl());
		data.put("cat", catalog.getCat());
		data.put("pageEncoding", catalog.getPageEncoding());
		data.put("maxFetchSize", catalog.getMaxFetchSize());
		data.put("duration", catalog.getDuration());
		data.put("depth", catalog.getDepth());
		data.put("interval", catalog.getInterval());
		data.put("version", indexEnabled ? getIndexVersion(catalogId) : 0);
		log.info("Catalog Config: {}", data);

		nioClient.send(Tuple.wrap(data), partitioner);
	}

	public void update(long catalogId, Condition extraCondition) {
		Catalog catalog = resourceManager.getCatalog(catalogId);
		Condition condition = extraCondition != null ? extraCondition : defaultCondition;
		condition.reset(catalogId, catalog.getDuration() != null ? catalog.getDuration().longValue() : DEFAULT_CRAWLER_IDLE_TIMEOUT);

		Map<String, Object> data = new HashMap<String, Object>();
		data.put(Tuple.PARTITIONER_NAME, HashPartitioner.class.getName());
		data.put("action", "update");
		data.put("catalogId", catalog.getId());
		data.put("refer", catalog.getUrl());
		data.put("path", getLatestPath(catalog.getId()));
		data.put("cat", catalog.getCat());
		data.put("pageEncoding", catalog.getPageEncoding());
		data.put("maxFetchSize", catalog.getMaxFetchSize());
		data.put("duration", catalog.getDuration());
		data.put("depth", catalog.getDepth());
		data.put("interval", catalog.getInterval());
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
