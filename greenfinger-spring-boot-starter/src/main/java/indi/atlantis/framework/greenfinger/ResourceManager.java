package indi.atlantis.framework.greenfinger;

import com.github.paganini2008.devtools.jdbc.PageResponse;

import indi.atlantis.framework.greenfinger.model.Catalog;
import indi.atlantis.framework.greenfinger.model.CatalogIndex;
import indi.atlantis.framework.greenfinger.model.Resource;

/**
 * 
 * ResourceManager
 *
 * @author Fred Feng
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
