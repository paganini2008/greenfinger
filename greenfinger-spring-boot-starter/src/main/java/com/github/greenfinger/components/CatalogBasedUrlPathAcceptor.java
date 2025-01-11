package com.github.greenfinger.components;

import org.springframework.core.Ordered;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.WebCrawlerProperties;
import com.github.greenfinger.model.Catalog;

/**
 * 
 * @Description: CatalogBasedUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
public abstract class CatalogBasedUrlPathAcceptor implements UrlPathAcceptor, Ordered {

    protected CatalogBasedUrlPathAcceptor(Catalog catalog,
            WebCrawlerProperties webCrawlerProperties) {
        this.catalog = catalog;
        this.webCrawlerProperties = webCrawlerProperties;
    }

    private final Catalog catalog;
    private final WebCrawlerProperties webCrawlerProperties;

    @Override
    public boolean accept(String referUrl, String path, Packet packet) {
        return accept(catalog, referUrl, path, packet);
    }

    protected Catalog getCatalog() {
        return catalog;
    }

    protected WebCrawlerProperties getWebCrawlerProperties() {
        return webCrawlerProperties;
    }

    protected abstract boolean accept(Catalog catalog, String referUrl, String path, Packet packet);

    @Override
    public int getOrder() {
        return 999;
    }

}
