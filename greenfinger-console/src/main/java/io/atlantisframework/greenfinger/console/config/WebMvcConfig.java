/**
* Copyright 2017-2022 Fred Feng (paganini.fy@gmail.com)

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

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.github.paganini2008.devtools.StringUtils;

import io.atlantisframework.greenfinger.api.Result;
import io.atlantisframework.greenfinger.console.utils.WebUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * WebMvcConfig
 * 
 * @author Fred Feng
 * 
 * @version 2.0.0
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/swagger-ui.html").addResourceLocations("classpath:/META-INF/resources/");
		registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
		registry.addResourceHandler("/static/**").addResourceLocations("classpath:/META-INF/static/");
	}

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.setUseSuffixPatternMatch(true).setUseTrailingSlashMatch(true);
	}

	@Bean
	public MultipartConfigElement multipartConfigElement() {
		MultipartConfigFactory factory = new MultipartConfigFactory();
		factory.setMaxFileSize(DataSize.parse("100MB"));
		factory.setMaxRequestSize(DataSize.parse("100MB"));
		return factory.createMultipartConfig();
	}

	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(signHandlerInterceptor()).addPathPatterns("/**").order(1);
		registry.addInterceptor(basicHandlerInterceptor()).addPathPatterns("/**").order(2);
	}

	@Bean
	public HandlerInterceptor basicHandlerInterceptor() {
		return new BasicHandlerInterceptor();
	}

	@Bean
	public HandlerInterceptor signHandlerInterceptor() {
		return new SignHandlerInterceptor();
	}

	public static class BasicHandlerInterceptor implements HandlerInterceptor, EnvironmentAware {

		private static final String ATTR_WEB_CONTEXT_PATH = "contextPath";

		private Environment environment;

		@Override
		public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
			HttpSession session = request.getSession();
			if (session.getAttribute(ATTR_WEB_CONTEXT_PATH) == null) {
				String webContextPath = environment.getProperty("atlantis.framework.greenginger.console.contextPath");
				if (StringUtils.isBlank(webContextPath)) {
					webContextPath = WebUtils.getContextPath(request);
				}
				session.setAttribute(ATTR_WEB_CONTEXT_PATH, webContextPath);
			}
			return true;
		}

		public void setEnvironment(Environment environment) {
			this.environment = environment;
		}

	}

	@Slf4j
	public static class SignHandlerInterceptor implements HandlerInterceptor {

		@Override
		public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
			request.setAttribute(REQUEST_ATTRIBUTE_START_TIME, System.currentTimeMillis());
			if (log.isTraceEnabled()) {
				log.trace(request.toString());
			}
			return true;
		}

		@Override
		public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
				throws Exception {
			if (log.isTraceEnabled()) {
				long startTime = (Long) request.getAttribute(REQUEST_ATTRIBUTE_START_TIME);
				log.trace(request.toString() + " take(ms): " + (System.currentTimeMillis() - startTime));
			}
		}

	}

	@Slf4j
	@RestControllerAdvice
	public static class NormalResponsePreHandler implements ResponseBodyAdvice<Object> {

		@Override
		public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
			return true;
		}

		@Override
		public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
				Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest req, ServerHttpResponse resp) {
			ServletServerHttpRequest request = (ServletServerHttpRequest) req;
			HttpServletRequest servletRequest = request.getServletRequest();
			long startTime = 0;
			if (log.isTraceEnabled()) {
				startTime = (Long) servletRequest.getAttribute("sign");
				log.trace("Path: " + servletRequest.getServletPath() + " take(ms): " + (System.currentTimeMillis() - startTime));
			}
			if (body instanceof Result) {
				Result<?> result = (Result<?>) body;
				result.setElapsed(startTime > 0 ? System.currentTimeMillis() - startTime : 0);
				result.setRequestPath(servletRequest.getServletPath());
			}
			return body;
		}

	}
}
