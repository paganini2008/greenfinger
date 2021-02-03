package org.springtribe.framework.greenfinger.jdbc;

import static org.springtribe.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_INSERT;
import static org.springtribe.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_MAX_VERSION;
import static org.springtribe.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_SELECT_ONE;
import static org.springtribe.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_UPDATE;
import static org.springtribe.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_VERSION_INCREMENT;

import org.springtribe.framework.greenfinger.model.CatalogIndex;
import org.springtribe.framework.jdbc.annotations.Arg;
import org.springtribe.framework.jdbc.annotations.Dao;
import org.springtribe.framework.jdbc.annotations.Example;
import org.springtribe.framework.jdbc.annotations.Get;
import org.springtribe.framework.jdbc.annotations.Update;

/**
 * 
 * CatalogIndexDao
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
@Dao
public interface CatalogIndexDao {

	@Update(SQL_CATALOG_INDEX_INSERT)
	int saveCatalogIndex(@Example CatalogIndex catalogIndex);

	@Update(SQL_CATALOG_INDEX_UPDATE)
	int updateCatalogIndex(@Example CatalogIndex catalogIndex);

	@Get(SQL_CATALOG_INDEX_SELECT_ONE)
	CatalogIndex getCatalogIndex(@Arg("catalogId") long catalogId);

	@Update(SQL_CATALOG_INDEX_VERSION_INCREMENT)
	int incrementCatalogIndexVersion(@Arg("catalogId") long catalogId);

	@Get(value = SQL_CATALOG_INDEX_MAX_VERSION, javaType = true)
	int maximunVersionOfCatalogIndex();

}
