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
package indi.atlantis.framework.greenfinger.console.config;

import static indi.atlantis.framework.greenfinger.console.WebConstants.REQUEST_ATTRIBUTE_START_TIME;

import javax.servlet.http.HttpServletRequest;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import indi.atlantis.framework.greenfinger.console.utils.Response;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * GlobalExceptionalHandler
 *
 * @author Fred Feng
 * @since 1.0
 */
@Slf4j
@Order(200)
@RestControllerAdvice
public class GlobalExceptionalHandler {

	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	@ExceptionHandler(value = Exception.class)
	public ResponseEntity<Response> handleException(HttpServletRequest request, Exception e) throws Exception {
		log.error(e.getMessage(), e);
		Response response = Response.failure("INTERNAL_SERVER_ERROR");
		response.setRequestPath(request.getServletPath()).setError(e).setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
		if (request.getAttribute(REQUEST_ATTRIBUTE_START_TIME) != null) {
			long startTime = (Long) request.getAttribute(REQUEST_ATTRIBUTE_START_TIME);
			response.setElapsed(System.currentTimeMillis() - startTime);
		}
		return new ResponseEntity<Response>(response, HttpStatus.OK);
	}

}