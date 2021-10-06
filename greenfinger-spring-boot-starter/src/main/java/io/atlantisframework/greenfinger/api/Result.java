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
package io.atlantisframework.greenfinger.api;

import lombok.Getter;
import lombok.Setter;

/**
 * 
 * Result
 * 
 * @author Fred Feng
 *
 * @since 2.0.1
 */
@Getter
@Setter
public class Result<T> {
	
	private boolean success;
	private String msg;
	private T data;
	private String code;
	private String errorMsg;
	private String[] errorDetail;
	private long elapsed;
	private String requestPath;
	private int responseStatus;
	
	public Result() {
	}
	
	public static <T> Result<T> success(T data){
		return success(data, "ok");
	}

	public static <T> Result<T> success(T data, String msg) {
		Result<T> model = new Result<T>();
		model.setSuccess(true);
		model.setMsg(msg);
		model.setData(data);
		return model;
	}

	public static <T> Result<T> failure(String msg) {
		Result<T> model = new Result<T>();
		model.setSuccess(false);
		model.setMsg(msg);
		return model;
	}

}
