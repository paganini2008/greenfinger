package com.github.greenfinger.components;

import java.util.Arrays;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.WebCrawlerProperties;
import com.github.greenfinger.model.Catalog;

/**
 * 
 * @Description: CatalogPatternUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public class CatalogPatternUrlPathAcceptor extends CatalogBasedUrlPathAcceptor {

    private final PathMatcher pathMather = new AntPathMatcher();
    private final List<String> excludedPathPatterns;
    private final List<String> pathPatterns;

    public CatalogPatternUrlPathAcceptor(Catalog catalog,
            WebCrawlerProperties webCrawlerProperties) {
        super(catalog, webCrawlerProperties);
        this.excludedPathPatterns = Arrays.asList(catalog.getExcludedPathPattern().split(","));
        this.pathPatterns = Arrays.asList(catalog.getPathPattern().split(","));
    }

    @Override
    public boolean accept(Catalog catalog, String referUrl, String path, Packet packet) {
        List<String> pathPatterns = this.excludedPathPatterns;
        for (String pathPattern : pathPatterns) {
            if (pathMather.match(pathPattern, path)) {
                return false;
            }
        }
        pathPatterns = this.pathPatterns;
        for (String pathPattern : pathPatterns) {
            if (pathMather.match(pathPattern, path)) {
                return true;
            }
        }
        if (CollectionUtils.isEmpty(pathPatterns)) {
            return path.startsWith(referUrl);
        }
        return false;
    }

    @Override
    public int getOrder() {
        return 1;
    }

    public static void main(String[] args) {
        PathMatcher pathMather = new AntPathMatcher();
        final String pattern = "https://**.tuniu.**/**";
        System.out.println(pathMather.match(pattern, "https://sina.tuniu.com/a/b"));
    }

}
