/**
* Copyright 2017-2021 Fred Feng (paganini.fy@gmail.com)

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
package io.atlantisframework.greenfinger.model;

import java.io.Serializable;
import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/**
 * 
 * Resource
 *
 * @author Fred Feng
 * 
 * @since 2.0.1
 */
@Getter
@Setter
public class Resource implements Serializable {

	private static final long serialVersionUID = -4629236151028422706L;
	private Long id;
	private String title;
	private String html;
	private String url;
	private String cat;
	private Date createTime;
	private Integer version;
	private Long catalogId;

	public String toString() {
		return "[Resource] id: " + id + ", title: " + title + ", url: " + url + ", version: " + version;
	}

}
