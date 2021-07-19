/**
* Copyright 2018-2021 Fred Feng (paganini.fy@gmail.com)

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
package indi.atlantis.framework.greenfinger.jdbc;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.github.paganini2008.devtools.jdbc.PageRequest;
import com.github.paganini2008.devtools.jdbc.PageResponse;
import com.github.paganini2008.devtools.jdbc.ResultSetSlice;
import com.github.paganini2008.springdessert.reditools.common.IdGenerator;

import indi.atlantis.framework.greenfinger.ResourceManager;
import indi.atlantis.framework.greenfinger.model.Catalog;
import indi.atlantis.framework.greenfinger.model.CatalogIndex;
import indi.atlantis.framework.greenfinger.model.Resource;

/**
 * 
 * JdbcResourceManger
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
@Transactional(rollbackFor = Exception.class, readOnly = false)
public class JdbcResourceManger implements ResourceManager {

	public static final String SQL_CATALOG_INSERT = "insert into crawler_catalog (id,name,url,path_pattern,excluded_path_pattern,cat,page_encoding,max_fetch_size,duration,last_modified) values (:id,:name,:url,:pathPattern,:excludedPathPattern,:cat,:pageEncoding,:maxFetchSize,:duration,:lastModified)";
	public static final String SQL_CATALOG_UPDATE = "update crawler_catalog set name=:name,url=:url,path_pattern=:pathPattern,excluded_path_pattern=:excludedPathPattern,cat=:cat,last_modified=:lastModified where id=:id";
	public static final String SQL_CATALOG_INDEX_INSERT = "insert into crawler_catalog_index (id,catalog_id,version,last_modified) values (:id,:catalogId,:version,:lastModified)";
	public static final String SQL_CATALOG_INDEX_UPDATE = "update crawler_catalog_index set version=:version,last_modified=:lastModified where id=:id";
	public static final String SQL_CATALOG_INDEX_VERSION_INCREMENT = "update crawler_catalog_index set version=version+1 where catalog_id=:catalogId";
	public static final String SQL_CATALOG_SELECT_ONE = "select * from crawler_catalog where id=:id limit 1";
	public static final String SQL_CATALOG_INDEX_SELECT_ONE = "select * from crawler_catalog_index where catalog_id=:catalogId";
	public static final String SQL_CATALOG_DELETE = "delete from crawler_catalog where id=:id";
	public static final String SQL_CATALOG_SELECT_ALL = "select * from crawler_catalog order by last_modified desc";
	public static final String SQL_CATALOG_INDEX_MAX_VERSION = "select max(version) from crawler_catalog_index";
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
	public long saveCatalog(Catalog catalog) {
		Date now = new Date();
		catalog.setId(idGenerator.generateId());
		catalog.setLastModified(now);
		catalogDao.saveCatalog(catalog);

		CatalogIndex catalogIndex = new CatalogIndex();
		catalogIndex.setId(idGenerator.generateId());
		catalogIndex.setCatalogId(catalog.getId());
		catalogIndex.setLastModified(now);
		catalogIndex.setVersion(1);
		catalogIndexDao.saveCatalogIndex(catalogIndex);
		return catalog.getId();
	}

	@Override
	public int deleteCatalog(long id) {
		return catalogDao.deleteCatalog(id);
	}

	@Override
	public Catalog getCatalog(long id) {
		return catalogDao.getCatalog(id);
	}

	@Override
	public PageResponse<Catalog> queryForCatalog(int page, int size) {
		ResultSetSlice<Catalog> resultSetSlice = catalogDao.queryForCatalog();
		return resultSetSlice.list(PageRequest.of(page, size));
	}

	@Override
	public int updateCatalogIndex(CatalogIndex catalogIndex) {
		catalogIndex.setLastModified(new Date());
		return catalogIndexDao.updateCatalogIndex(catalogIndex);
	}

	@Override
	public CatalogIndex getCatalogIndex(long catalogId) {
		return catalogIndexDao.getCatalogIndex(catalogId);
	}

	@Override
	public int maximumVersionOfCatalogIndex() {
		return catalogIndexDao.maximunVersionOfCatalogIndex();
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
	public PageResponse<Resource> queryForResourceForIndex(long catalogId, int page, int size) {
		ResultSetSlice<Resource> resultSetSlice = resourceDao.queryForResourceForIndex(catalogId);
		return resultSetSlice.list(PageRequest.of(page, size));
	}

	@Override
	public int updateResourceVersion(long catalogId, int version) {
		return resourceDao.updateResourceVersion(catalogId, version);
	}

	@Override
	public int incrementCatalogIndexVersion(long catalogId) {
		return catalogIndexDao.incrementCatalogIndexVersion(catalogId);
	}

	@Override
	public String getLatestPath(long catalogId) {
		return resourceDao.getLatestPath(catalogId);
	}

}
