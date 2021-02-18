package indi.atlantis.framework.greenfinger.jdbc;

import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_INSERT;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_MAX_VERSION;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_SELECT_ONE;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_UPDATE;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_VERSION_INCREMENT;

import indi.atlantis.framework.greenfinger.model.CatalogIndex;
import indi.atlantis.framework.jdbc.annotations.Arg;
import indi.atlantis.framework.jdbc.annotations.Dao;
import indi.atlantis.framework.jdbc.annotations.Example;
import indi.atlantis.framework.jdbc.annotations.Get;
import indi.atlantis.framework.jdbc.annotations.Update;

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
