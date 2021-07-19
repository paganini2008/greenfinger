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
package indi.atlantis.framework.greenfinger;

import org.springframework.http.HttpStatus;

/**
 * 
 * PageExtractorException
 *
 * @author Fred Feng
 * 
 * @since 1.0
 */
public class PageExtractorException extends RuntimeException {

	private static final long serialVersionUID = 4816595505153970862L;
	private static final String msgFormat = "%s [%s: %s]";

	public PageExtractorException(String url, HttpStatus httpStatus) {
		super(String.format(msgFormat, url, httpStatus.value(), httpStatus.getReasonPhrase()));
		this.httpStatus = httpStatus;
	}

	private final HttpStatus httpStatus;

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

}
