package com.github.greenfinger.test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.MapUtils;
import com.github.greenfinger.ResourceManager;
import com.github.greenfinger.model.Catalog;

/**
 * 
 * @Description: DefaultCatalogUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public class DefaultCatalogUrlPathAcceptor extends CatalogBasedUrlPathAcceptor {

    private final PathMatcher pathMather = new AntPathMatcher();

    private final Map<Long, List<String>> pathPatternCache =
            new ConcurrentHashMap<Long, List<String>>();
    private final Map<Long, List<String>> excludedPathPatternCache =
            new ConcurrentHashMap<Long, List<String>>();

    public DefaultCatalogUrlPathAcceptor(ResourceManager resourceManager) {
        super(resourceManager);
    }

    @Override
    public boolean accept(Catalog catalog, String refer, String path, Packet packet) {
        List<String> pathPatterns =
                MapUtils.getOrCreate(excludedPathPatternCache, catalog.getId(), () -> {
                    if (StringUtils.isBlank(catalog.getExcludedPathPattern())) {
                        return Collections.emptyList();
                    }
                    return Arrays.asList(catalog.getExcludedPathPattern().split(","));
                });
        for (String pathPattern : pathPatterns) {
            if (pathMather.match(pathPattern, path)) {
                return false;
            }
        }

        pathPatterns = MapUtils.getOrCreate(pathPatternCache, catalog.getId(), () -> {
            if (StringUtils.isBlank(catalog.getPathPattern())) {
                return Collections.emptyList();
            }
            return Arrays.asList(catalog.getPathPattern().split(","));
        });

        if (CollectionUtils.isEmpty(pathPatterns)) {
            return path.startsWith(refer);
        }
        for (String pathPattern : pathPatterns) {
            if (pathMather.match(pathPattern, path)) {
                return true;
            }
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
