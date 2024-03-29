drop table if exists crawler_catalog;
create table crawler_catalog(
	id bigint not null,
	name character varying(255) not null,
	url character varying(255) not null,
	path_pattern character varying(600),
	excluded_path_pattern character varying(600),
	cat character varying(45) not null,
	page_encoding character varying(45),
	max_fetch_size integer,
	duration bigint,
	interval bigint,
	depth integer,
	last_modified timestamp without time zone
);

drop table if exists crawler_catalog_index;
create table crawler_catalog_index(
	id bigint not null,
	catalog_id bigint not null,
	last_modified timestamp without time zone,
	version integer not null
);

drop table if exists crawler_resource;
create table crawler_resource(
	id bigint not null,
	title character varying(600) not null,
	html text,
	url character varying(600),
	cat character varying(45) not null,
	create_time timestamp without time zone,
	version integer not null,
	catalog_id bigint not null
);

