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

import java.util.List;

import com.github.paganini2008.devtools.jdbc.PageResponse;

import indi.atlantis.framework.greenfinger.model.Catalog;
import indi.atlantis.framework.greenfinger.model.CatalogIndex;
import indi.atlantis.framework.greenfinger.model.CatalogInfo;
import indi.atlantis.framework.greenfinger.model.Resource;

/**
 * 
 * ResourceManager
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
public interface ResourceManager {

	long saveCatalog(Catalog catalog);

	int deleteCatalog(long id);

	Catalog getCatalog(long id);

	PageResponse<CatalogInfo> selectForCatalog(Catalog example, int page, int size);

	List<String> selectAllCats();

	int updateCatalogIndex(CatalogIndex catalogIndex);

	int maximumVersionOfCatalogIndex(String cat);

	CatalogIndex getCatalogIndex(long catalogId);

	int getCatalogIndexVersion(long catalogId);

	int saveResource(Resource resource);

	Resource getResource(long id);

	int deleteResourceByCatalogId(long catalogId);

	PageResponse<Resource> selectForResourceForIndex(long catalogId, int page, int size);

	int updateResourceVersion(long catalogId, int version);

	int incrementCatalogIndexVersion(long catalogId);

	String getLatestPath(long catalogId);

}
