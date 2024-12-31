/**
 * Copyright 2017-2022 Fred Feng (paganini.fy@gmail.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.github.greenfinger.ui.config;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.github.greenfinger.ui.utils.WebUtils;

/**
 * 
 * @Description: WebMvcConfig
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/META-INF/static/");
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
        registry.addInterceptor(basicHandlerInterceptor()).addPathPatterns("/**").order(1);
    }

    @Bean
    public HandlerInterceptor basicHandlerInterceptor() {
        return new BasicHandlerInterceptor();
    }

    public static class BasicHandlerInterceptor implements HandlerInterceptor, EnvironmentAware {

        private static final String ATTR_WEB_CONTEXT_PATH = "contextPath";

        private Environment environment;

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                Object handler) throws Exception {
            HttpSession session = request.getSession();
            if (session.getAttribute(ATTR_WEB_CONTEXT_PATH) == null) {
                String webContextPath = environment
                        .getProperty("atlantis.framework.greenginger.console.contextPath");
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
}
