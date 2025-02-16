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

package com.github.greenfinger.model;

import java.io.Serializable;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 
 * @Description: CatalogIndex
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
@Getter
@Setter
@ToString
public class CatalogIndex implements Serializable {

    private static final long serialVersionUID = 599930283370705308L;

    private Long id;
    private Long catalogId;
    private Date lastModified;
    private Integer version;

    public CatalogIndex(Long catalogId, Integer version, Date lastModified) {
        this.catalogId = catalogId;
        this.version = version;
        this.lastModified = lastModified;
    }

    public CatalogIndex() {}

}
