package indi.atlantis.framework.greenfinger.jdbc;

import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_INSERT;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_MAX_VERSION;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_SELECT_ONE;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_UPDATE;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INDEX_VERSION_INCREMENT;

import com.github.paganini2008.springdesert.jdbc.annotations.Arg;
import com.github.paganini2008.springdesert.jdbc.annotations.Dao;
import com.github.paganini2008.springdesert.jdbc.annotations.Example;
import com.github.paganini2008.springdesert.jdbc.annotations.Get;
import com.github.paganini2008.springdesert.jdbc.annotations.Update;

import indi.atlantis.framework.greenfinger.model.CatalogIndex;

/**
 * 
 * CatalogIndexDao
 *
 * @author Fred Feng
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
