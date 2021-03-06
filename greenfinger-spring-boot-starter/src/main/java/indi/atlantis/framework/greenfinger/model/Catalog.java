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
package indi.atlantis.framework.greenfinger.model;

import java.io.Serializable;
import java.util.Date;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 
 * Catalog
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
@Getter
@Setter
@ToString
public class Catalog implements Serializable {

	private static final long serialVersionUID = 1980884447290929341L;
	private Long id;
	private String name;
	private String cat;
	private String url;
	private String pageEncoding;
	private String pathPattern;
	private String excludedPathPattern;
	private Integer maxFetchSize;
	private Long duration;
	private Date lastModified;

}
