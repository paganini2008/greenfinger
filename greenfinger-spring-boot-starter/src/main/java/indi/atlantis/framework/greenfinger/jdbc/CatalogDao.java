package indi.atlantis.framework.greenfinger.jdbc;

import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_DELETE;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INSERT;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_SELECT_ALL;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_SELECT_ONE;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_UPDATE;

import com.github.paganini2008.devtools.jdbc.ResultSetSlice;

import indi.atlantis.framework.greenfinger.model.Catalog;
import indi.atlantis.framework.jdbc.annotations.Arg;
import indi.atlantis.framework.jdbc.annotations.Dao;
import indi.atlantis.framework.jdbc.annotations.Example;
import indi.atlantis.framework.jdbc.annotations.Get;
import indi.atlantis.framework.jdbc.annotations.Query;
import indi.atlantis.framework.jdbc.annotations.Update;

/**
 * 
 * CatalogDao
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
@Dao
public interface CatalogDao {

	@Update(SQL_CATALOG_INSERT)
	int saveCatalog(@Example Catalog catalog);

	@Update(SQL_CATALOG_UPDATE)
	int updateCatalog(@Example Catalog catalog);

	@Update(SQL_CATALOG_DELETE)
	int deleteCatalog(@Arg("id") long id);

	@Get(SQL_CATALOG_SELECT_ONE)
	Catalog getCatalog(@Arg("id") long id);

	@Query(SQL_CATALOG_SELECT_ALL)
	ResultSetSlice<Catalog> queryForCatalog();

}
