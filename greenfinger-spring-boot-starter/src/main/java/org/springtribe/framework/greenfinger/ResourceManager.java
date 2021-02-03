package org.springtribe.framework.greenfinger;

import org.springtribe.framework.greenfinger.model.Catalog;
import org.springtribe.framework.greenfinger.model.CatalogIndex;
import org.springtribe.framework.greenfinger.model.Resource;

import com.github.paganini2008.devtools.jdbc.PageResponse;

/**
 * 
 * ResourceManager
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
public interface ResourceManager {

	long saveCatalog(Catalog catalog);

	int deleteCatalog(long id);

	Catalog getCatalog(long id);

	PageResponse<Catalog> queryForCatalog(int page, int size);

	int updateCatalogIndex(CatalogIndex catalogIndex);

	int maximumVersionOfCatalogIndex();

	CatalogIndex getCatalogIndex(long catalogId);

	int saveResource(Resource resource);

	Resource getResource(long id);

	int deleteResourceByCatalogId(long catalogId);

	PageResponse<Resource> queryForResourceForIndex(long catalogId, int page, int size);

	int updateResourceVersion(long catalogId, int version);

	int incrementCatalogIndexVersion(long catalogId);

	String getLatestPath(long catalogId);

}
