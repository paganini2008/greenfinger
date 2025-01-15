drop table if exists crawler_catalog;
create table crawler_catalog(
	id bigint primary key,
	name varchar(255) not null,
	url varchar(255) not null,
	path_pattern varchar(2000) not null,
	excluded_path_pattern varchar(2000),
	cat varchar(45) not null,
	page_encoding varchar(45) default 'UTF-8',
	max_fetch_size int2,
	duration bigint,
	interval bigint,
	depth int2 default -1,
	counting_type int2,
    max_retry_count int2 default 0,
    url_path_acceptor varchar(2000),
    url_path_filter varchar(45) default 'redission-bloomfilter',
    extractor varchar(45) default 'resttemplate',
    credential_handler varchar(1000),
    running_state varchar(45),
    indexed boolean default true,
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
	title varchar(1000) not null,
	html text,
	url varchar(1000),
	cat varchar(45) not null,
	create_time timestamp without time zone,
	version integer not null,
	catalog_id bigint not null,
	constraint constraint_url_rule unique (catalog_id, cat, version, title, url)
);

