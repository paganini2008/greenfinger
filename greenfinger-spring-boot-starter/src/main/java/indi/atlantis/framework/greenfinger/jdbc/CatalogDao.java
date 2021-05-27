package indi.atlantis.framework.greenfinger.jdbc;

import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_DELETE;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_INSERT;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_SELECT_ALL;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_SELECT_ONE;
import static indi.atlantis.framework.greenfinger.jdbc.JdbcResourceManger.SQL_CATALOG_UPDATE;

import com.github.paganini2008.devtools.jdbc.ResultSetSlice;
import com.github.paganini2008.springworld.jdbc.annotations.Arg;
import com.github.paganini2008.springworld.jdbc.annotations.Dao;
import com.github.paganini2008.springworld.jdbc.annotations.Example;
import com.github.paganini2008.springworld.jdbc.annotations.Get;
import com.github.paganini2008.springworld.jdbc.annotations.Select;
import com.github.paganini2008.springworld.jdbc.annotations.Update;

import indi.atlantis.framework.greenfinger.model.Catalog;

/**
 * 
 * CatalogDao
 *
 * @author Fred Feng
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

	@Select(SQL_CATALOG_SELECT_ALL)
	ResultSetSlice<Catalog> queryForCatalog();

}
