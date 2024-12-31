package com.github.greenfinger.es;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;
import lombok.Getter;
import lombok.Setter;

/**
 * 
 * @Description: IndexedResource
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@Getter
@Setter
@Setting(refreshInterval = "10s")
@Document(indexName = "webcrawler_resource_0")
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
