/**
* Copyright 2018-2021 Fred Feng (paganini.fy@gmail.com)

* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package indi.atlantis.framework.greenfinger.es;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

/**
 * 
 * SearchResult
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
@Getter
@Setter
public class SearchResult implements Serializable {

	private static final long serialVersionUID = 5993548637885933491L;
	public static final String SEARCH_FIELD_TITLE = "title";
	public static final String SEARCH_FIELD_CONTENT = "content";
	public static final String SEARCH_FIELD_CAT = "cat";
	public static final String SEARCH_FIELD_CATALOG = "catalog";
	public static final String SEARCH_FIELD_VERSION = "version";

	private Long id;
	private String title;
	private String content;
	private String url;
	private String path;
	private String cat;
	private String catalog;
	private Integer version;
	private Long createTime;
}
