package org.springtribe.framework.greenfinger.es;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import lombok.Getter;
import lombok.Setter;

/**
 * 
 * IndexedResource
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
@Getter
@Setter
@Document(indexName = "webcrawler_resource_0", type = "resource", shards = 3, replicas = 1)
public class IndexedResource {

	@Id
	@Field(type = FieldType.Long, store = true)
	private Long id;

	@Field(type = FieldType.Text, store = true, analyzer = "ik_smart", searchAnalyzer = "ik_smart")
	private String title;

	@Field(type = FieldType.Text, store = true, analyzer = "ik_smart", searchAnalyzer = "ik_smart")
	private String content;

	@Field(type = FieldType.Keyword, store = true)
	private String url;

	@Field(type = FieldType.Keyword, store = true)
	private String path;

	@Field(type = FieldType.Keyword, store = true)
	private String cat;

	@Field(type = FieldType.Keyword, store = true)
	private String catalog;

	@Field(type = FieldType.Integer, store = true)
	private Integer version;

	@Field(type = FieldType.Long, store = true)
	private Long createTime;

	public String toString() {
		return "[IndexedResource] title: " + title + ", url: " + url + ", version: " + version;
	}

}
