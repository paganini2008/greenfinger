package org.springtribe.framework.greenfinger.jdbc;

import static org.springtribe.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_DELETE;
import static org.springtribe.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INSERT;
import static org.springtribe.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_SELECT_ALL;
import static org.springtribe.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_SELECT_ONE;
import static org.springtribe.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_UPDATE;

import org.springtribe.framework.greenfinger.model.Catalog;
import org.springtribe.framework.jdbc.annotations.Arg;
import org.springtribe.framework.jdbc.annotations.Dao;
import org.springtribe.framework.jdbc.annotations.Example;
import org.springtribe.framework.jdbc.annotations.Get;
import org.springtribe.framework.jdbc.annotations.Query;
import org.springtribe.framework.jdbc.annotations.Update;

import com.github.paganini2008.devtools.jdbc.ResultSetSlice;

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
