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

import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.context.annotation.Bean;
import com.github.greenfinger.api.security.WebSecurityConfiguration;
import com.github.greenfinger.output.OutputFactory;
import com.github.greenfinger.core.WebCrawlerException;
import com.github.greenfinger.core.catalog.CatalogDetailsNotFoundException;
import lombok.extern.slf4j.Slf4j;

/**
 * The http face of the crawler, added only when there is a web application to add it to -- the
 * command line runs the identical services with no servlet container in sight.
 * 
 * @Description: GreenfingerWebConfiguration
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnWebApplication
@Import({WebSecurityConfiguration.class, SinglePageAppConfiguration.class,
        AuthApiController.class, MetaApiController.class, CatalogApiController.class,
        CrawlApiController.class, SearchApiController.class, ImageApiController.class,
        GreenfingerWebConfiguration.ApiExceptionHandler.class})
public class GreenfingerWebConfiguration {

    /**
     * One embedding client for the whole server rather than one per search. See the class itself
     * for why a web application cannot do what the command line does here.
     */
    @Bean
    public VectorSearchSupport vectorSearchSupport(OutputFactory outputFactory) {
        return new VectorSearchSupport(outputFactory);
    }

    /**
     * Turns the crawler's own exceptions into the same envelope everything else returns, so a front
     * end never has to parse a stack trace.
     * 
     * @Description: ApiExceptionHandler
     * @Author: Fred Feng
     * @Date: 30/08/2026
     * @Version 2.0.0
     */
    @Slf4j
    @RestControllerAdvice
    public static class ApiExceptionHandler {

        /**
         * A wrong password, or a token that is no longer live. One message for both: which of the
         * two it was is of interest to nobody but somebody guessing.
         */
        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ApiResult<Void>> unauthenticated(AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResult.failed("Wrong username or password"));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResult<Void>> invalid(MethodArgumentNotValidException e) {
            String message = e.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage).distinct()
                    .collect(Collectors.joining("; "));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResult.failed(message));
        }

        @ExceptionHandler(CatalogDetailsNotFoundException.class)
        public ResponseEntity<ApiResult<Void>> notFound(CatalogDetailsNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResult.failed(e.getMessage()));
        }

        @ExceptionHandler(WebCrawlerException.class)
        public ResponseEntity<ApiResult<Void>> crawlerFailure(WebCrawlerException e) {
            // a refused delete or a second concurrent crawl: the caller's problem, not the server's
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResult.failed(e.getMessage()));
        }

        /**
         * Spring's own answers -- a url with no handler above all -- keep the status they came
         * with. Without this they fall into the catch-all below and a missing endpoint is
         * reported as a server failure, which sends whoever mistyped it looking for a bug.
         */
        @ExceptionHandler(ErrorResponseException.class)
        public ResponseEntity<ApiResult<Void>> errorResponse(ErrorResponseException e) {
            return ResponseEntity.status(e.getStatusCode()).body(ApiResult.failed(e.getMessage()));
        }

        /**
         * Listed separately because it is the one that matters here and, unlike the rest, does not
         * extend {@link ErrorResponseException}: it is what an api path with no handler raises
         * once the page-serving resource handler has declined it.
         */
        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ApiResult<Void>> noResource(NoResourceFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResult.failed("No such endpoint: " + e.getResourcePath()));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResult<Void>> unexpected(Exception e) {
            log.error("Request failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResult.failed(e.getMessage()));
        }

    }

}
