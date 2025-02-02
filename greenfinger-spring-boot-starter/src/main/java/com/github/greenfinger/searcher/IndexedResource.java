/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger.searcher;

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
