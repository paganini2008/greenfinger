/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
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

package com.github.greenfinger.api.web;

/**
 * The envelope every endpoint returns, as in 1.x: a flag, a message and the payload, so a front end
 * has one shape to handle rather than a status code and a body that vary together.
 * 
 * @Description: ApiResult
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public record ApiResult<T>(boolean success, String message, T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, "ok", data);
    }

    public static <T> ApiResult<T> ok() {
        return new ApiResult<>(true, "ok", null);
    }

    public static <T> ApiResult<T> failed(String message) {
        return new ApiResult<>(false, message, null);
    }

}
