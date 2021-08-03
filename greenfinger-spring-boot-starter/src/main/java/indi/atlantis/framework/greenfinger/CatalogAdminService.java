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

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;

import indi.atlantis.framework.greenfinger.es.IndexedResourceService;
import indi.atlantis.framework.greenfinger.model.CatalogIndex;

/**
 * 
 * CatalogAdminService
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
public class CatalogAdminService {

	@Autowired
	private ResourceManager resourceManager;

	@Autowired
	private IndexedResourceService indexedResourceService;

	@Autowired
	private PathFilterFactory pathFilterFactory;

	public void cleanCatalog(long catalogId, boolean retainIndex) {
		resourceManager.deleteResourceByCatalogId(catalogId);
		pathFilterFactory.clean(catalogId);
		if (!retainIndex) {
			indexedResourceService.deleteResource(catalogId, 0);
			resourceManager.updateCatalogIndex(new CatalogIndex(catalogId, 0, new Date()));
		}
	}

	public void deleteCatalog(long catalogId, boolean retainIndex) {
		resourceManager.deleteResourceByCatalogId(catalogId);
		pathFilterFactory.clean(catalogId);
		resourceManager.deleteCatalog(catalogId);
		if (!retainIndex) {
			indexedResourceService.deleteResource(catalogId, 0);
		}
	}

}
