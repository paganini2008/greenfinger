package com.github.greenfinger;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 
 * @Description: GreenfingerUIConfiguration
 * @Author: Fred Feng
 * @Date: 30/01/2025
 * @Version 1.0.0
 */
@Configuration
public class GreenfingerUIConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/static/ui/favicon.ico");
        registry.addResourceHandler("/README.md")
                .addResourceLocations("classpath:/static/ui/README.md");
        registry.addResourceHandler("/ui/**").addResourceLocations("classpath:/static/ui/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/ui/about-me").setViewName("forward:/ui/index.html");
        registry.addViewController("/ui/login").setViewName("forward:/ui/index.html");
        registry.addViewController("/ui/catalog/**").setViewName("forward:/ui/index.html");
    }
}
