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
package io.atlantisframework.greenfinger.jdbc;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.github.paganini2008.devtools.StringUtils;
import com.github.paganini2008.devtools.beans.BeanUtils;
import com.github.paganini2008.devtools.jdbc.PageRequest;
import com.github.paganini2008.devtools.jdbc.PageResponse;
import com.github.paganini2008.devtools.jdbc.ResultSetSlice;
import com.github.paganini2008.springdessert.reditools.common.IdGenerator;

import io.atlantisframework.greenfinger.ResourceManager;
import io.atlantisframework.greenfinger.api.CatalogInfo;
import io.atlantisframework.greenfinger.model.Catalog;
import io.atlantisframework.greenfinger.model.CatalogIndex;
import io.atlantisframework.greenfinger.model.Resource;

/**
 * 
 * JdbcResourceManger
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
@Transactional(rollbackFor = Exception.class, readOnly = false)
public class JdbcResourceManger implements ResourceManager {

	public static final String SQL_CATALOG_INSERT = "insert into crawler_catalog (id,name,url,path_pattern,excluded_path_pattern,cat,page_encoding,max_fetch_size,duration,interval,depth,last_modified) values (:id,:name,:url,:pathPattern,:excludedPathPattern,:cat,:pageEncoding,:maxFetchSize,:duration,:interval,:depth,:lastModified)";
	public static final String SQL_CATALOG_UPDATE = "update crawler_catalog set name=:name,cat=:cat,url=:url,path_pattern=:pathPattern,excluded_path_pattern=:excludedPathPattern,max_fetch_size=:maxFetchSize,duration=:duration,interval=:interval,depth=:depth,last_modified=:lastModified where id=:id";
	public static final String SQL_CATALOG_INDEX_INSERT = "insert into crawler_catalog_index (id,catalog_id,version,last_modified) values (:id,:catalogId,:version,:lastModified)";
	public static final String SQL_CATALOG_INDEX_UPDATE = "update crawler_catalog_index set version=:version,last_modified=:lastModified where catalog_id=:catalogId";
	public static final String SQL_CATALOG_INDEX_VERSION_INCREMENT = "update crawler_catalog_index set version=version+1, last_modified=:lastModified where catalog_id=:catalogId";
	public static final String SQL_CATALOG_INDEX_VERSION_SELECT = "select version from crawler_catalog_index where catalog_id=:catalogId";
	public static final String SQL_CATALOG_SELECT_ONE = "select * from crawler_catalog where id=:id limit 1";
	public static final String SQL_CATALOG_CAT_SELECT = "select distinct cat from crawler_catalog";
	public static final String SQL_CATALOG_INDEX_SELECT_ONE = "select * from crawler_catalog_index where catalog_id=:catalogId";
	public static final String SQL_CATALOG_DELETE = "delete from crawler_catalog where id=:id";
	public static final String SQL_CATALOG_INDEX_DELETE = "delete from crawler_catalog_index where catalog_id=:catalogId";
	public static final String SQL_CATALOG_SELECT_ALL = "select a.*,b.version,b.last_modified as lastIndexed from crawler_catalog a join crawler_catalog_index b on a.id=b.catalog_id where 1=1 @sql order by a.last_modified desc,b.last_modified desc";
	public static final String SQL_CATALOG_INDEX_MAX_VERSION = "select max(a.version) from crawler_catalog_index a join crawler_catalog b on a.catalog_id=b.id where b.cat=:cat";
	public static final String SQL_RESOURCE_INSERT = "insert into crawler_resource (id,title,html,url,cat,create_time,version,catalog_id) values (:id,:title,:html,:url,:cat,:createTime,:version,:catalogId)";
	public static final String SQL_RESOURCE_SELECT_FOR_INDEX = "select * from crawler_resource where catalog_id=:catalogId and version<(select version from crawler_catalog_index where catalog_id=:catalogId)";
	public static final String SQL_RESOURCE_SELECT_ONE = "select * from crawler_resource where id=:id limit 1";
	public static final String SQL_RESOURCE_LATEST_PATH = "select url from crawler_resource where catalog_id=:catalogId order by create_time desc limit 1";
	public static final String SQL_RESOURCE_DELETE_ALL = "delete from crawler_resource where catalog_id=:catalogId";
	public static final String SQL_RESOURCE_VERSION_UPDATE = "update crawler_resource set version=:version where catalog_id=:catalogId and version<:version";

	@Autowired
	private CatalogDao catalogDao;

	@Autowired
	private CatalogIndexDao catalogIndexDao;

	@Autowired
	private ResourceDao resourceDao;

	@Autowired
	private IdGenerator idGenerator;

	@Override
	public long saveCatalog(Catalog source) {
		Date now = new Date();
		Catalog catalog;
		if (source.getId() != null) {
			catalog = getCatalog(source.getId());
			BeanUtils.copyProperties(source, catalog);
			catalog.setLastModified(now);
			catalogDao.updateCatalog(catalog);
		} else {
			catalog = source;
			catalog.setId(idGenerator.generateId());
			catalog.setLastModified(now);
			catalogDao.saveCatalog(catalog);

			CatalogIndex catalogIndex = new CatalogIndex();
			catalogIndex.setId(idGenerator.generateId());
			catalogIndex.setCatalogId(catalog.getId());
			catalogIndex.setLastModified(now);
			catalogIndex.setVersion(0);
			catalogIndexDao.saveCatalogIndex(catalogIndex);
		}
		return catalog.getId();
	}

	@Override
	public int deleteCatalog(long id) {
		int row = 0;
		if ((row += catalogDao.deleteCatalog(id)) > 0) {
			row += catalogIndexDao.deleteCatalogIndex(id);
			return row;
		}
		return row;
	}

	@Override
	public Catalog getCatalog(long id) {
		return catalogDao.getCatalog(id);
	}

	@Override
	public PageResponse<CatalogInfo> selectForCatalog(Catalog example, int page, int size) {
		StringBuilder whereClause = new StringBuilder();
		if (example != null) {
			if (StringUtils.isNotBlank(example.getName())) {
				example.setName("%" + example.getName() + "%");
				whereClause.append(" and a.name like :name");
			}
			if (StringUtils.isNotBlank(example.getCat())) {
				whereClause.append(" and a.cat=:cat");
			}
			if (StringUtils.isNotBlank(example.getUrl())) {
				example.setUrl("%" + example.getUrl() + "%");
				whereClause.append(" and a.url like :url");
			}
		}
		ResultSetSlice<CatalogInfo> resultSetSlice = catalogDao.selectForCatalog(whereClause.toString(), example);
		return resultSetSlice.list(PageRequest.of(page, size));
	}

	@Override
	public List<String> selectAllCats() {
		return catalogDao.selectAllCats();
	}

	@Override
	public int updateCatalogIndex(CatalogIndex catalogIndex) {
		if (catalogIndex.getLastModified() == null) {
			catalogIndex.setLastModified(new Date());
		}
		return catalogIndexDao.updateCatalogIndex(catalogIndex);
	}

	@Override
	public CatalogIndex getCatalogIndex(long catalogId) {
		return catalogIndexDao.getCatalogIndex(catalogId);
	}

	@Override
	public int maximumVersionOfCatalogIndex(String cat) {
		return catalogIndexDao.maximunVersionOfCatalogIndex(cat);
	}

	@Override
	public int getCatalogIndexVersion(long catalogId) {
		return catalogIndexDao.getCatalogIndexVersion(catalogId);
	}

	@Override
	public int saveResource(Resource resource) {
		resource.setId(idGenerator.generateId());
		return resourceDao.saveResource(resource);
	}

	@Override
	public Resource getResource(long id) {
		return resourceDao.getResource(id);
	}

	@Override
	public int deleteResourceByCatalogId(long catalogId) {
		return resourceDao.deleteResourceByCatalogId(catalogId);
	}

	@Override
	public PageResponse<Resource> selectForResourceForIndex(long catalogId, int page, int size) {
		ResultSetSlice<Resource> resultSetSlice = resourceDao.queryForResourceForIndex(catalogId);
		return resultSetSlice.list(PageRequest.of(page, size));
	}

	@Override
	public int updateResourceVersion(long catalogId, int version) {
		return resourceDao.updateResourceVersion(catalogId, version);
	}

	@Override
	public int incrementCatalogIndexVersion(long catalogId) {
		catalogIndexDao.incrementCatalogIndexVersion(new Date(), catalogId);
		return catalogIndexDao.getCatalogIndexVersion(catalogId);
	}

	@Override
	public String getLatestPath(long catalogId) {
		return resourceDao.getLatestPath(catalogId);
	}

}
