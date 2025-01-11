package com.github.greenfinger.components;

import java.util.Optional;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.WebCrawlerProperties;
import com.github.greenfinger.model.Catalog;

/**
 * 
 * @Description: DepthUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
public class DepthUrlPathAcceptor extends CatalogBasedUrlPathAcceptor {

    public DepthUrlPathAcceptor(Catalog catalog, WebCrawlerProperties webCrawlerProperties) {
        super(catalog, webCrawlerProperties);
    }

    @Override
    protected boolean accept(Catalog catalog, String referUrl, String path, Packet packet) {
        final int depth = Optional.ofNullable(catalog.getDepth())
                .orElse(getWebCrawlerProperties().getDefaultFetchDepth()).intValue();
        if (depth < 0) {
            return true;
        }
        String part = path.replace(referUrl, "");
        if (part.charAt(0) == '/') {
            part = part.substring(1);
        }
        int n = 0;
        for (char ch : part.toCharArray()) {
            if (ch == '/') {
                n++;
            }
        }
        return n <= depth;
    }

    @Override
    public int getOrder() {
        return 0;
    }

}
