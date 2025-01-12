package com.github.greenfinger.components;

import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: CatalogPatternUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public class CatalogPatternUrlPathAcceptor implements UrlPathAcceptor {

    private final PathMatcher pathMather = new AntPathMatcher();

    @Override
    public boolean accept(CatalogDetails catalogDetails, String referUrl, String url,
            Packet packet) {
        List<String> pathPatterns = catalogDetails.getExcludedPathPatterns();
        for (String pathPattern : pathPatterns) {
            if (pathMather.match(pathPattern, url)) {
                return false;
            }
        }
        pathPatterns = catalogDetails.getPathPatterns();
        for (String pathPattern : pathPatterns) {
            if (pathMather.match(pathPattern, url)) {
                return true;
            }
        }
        if (CollectionUtils.isEmpty(pathPatterns)) {
            return url.startsWith(referUrl);
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
