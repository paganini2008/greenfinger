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
package io.atlantisframework.greenfinger.console.config;

import static io.atlantisframework.greenfinger.console.utils.WebConstants.REQUEST_ATTRIBUTE_START_TIME;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.error.AbstractErrorController;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.paganini2008.devtools.StringUtils;

import io.atlantisframework.greenfinger.api.Result;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * ErrorPageController
 * 
 * @author Fred Feng
 *
 * @since 2.0.1
 */
@Slf4j
@RestController
public class ErrorPageController extends AbstractErrorController {

	private static final String ERROR_PATH = "/error";

	@Autowired
	public ErrorPageController(ErrorAttributes errorAttributes) {
		super(errorAttributes);
	}

	@Override
	public String getErrorPath() {
		return ERROR_PATH;
	}

	@RequestMapping(value = ERROR_PATH, produces = { MediaType.APPLICATION_JSON_VALUE })
	public ResponseEntity<Result<?>> error(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		final Map<String, Object> body = getErrorAttributes(httpRequest, true);
		log.error("ErrorAttributes: " + body.toString());
		HttpStatus httpStatus = HttpStatus.valueOf(httpResponse.getStatus());
		Result<?> result = Result.failure(httpStatus.getReasonPhrase());
		result.setCode(httpStatus.name());
		String path = (String) body.get("path");
		if (StringUtils.isBlank(path)) {
			path = httpRequest.getServletPath();
		}
		result.setRequestPath(path);
		result.setResponseStatus(httpStatus.value());
		if (httpRequest.getAttribute(REQUEST_ATTRIBUTE_START_TIME) != null) {
			long startTime = (Long) httpRequest.getAttribute(REQUEST_ATTRIBUTE_START_TIME);
			result.setElapsed(System.currentTimeMillis() - startTime);
		}
		return new ResponseEntity<Result<?>>(result, HttpStatus.OK);
	}

}
