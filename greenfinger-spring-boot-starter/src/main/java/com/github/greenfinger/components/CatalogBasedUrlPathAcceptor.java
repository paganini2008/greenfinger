package com.github.greenfinger.components;

import org.springframework.core.Ordered;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.ResourceManager;
import com.github.greenfinger.model.Catalog;

/**
 * 
 * @Description: CatalogBasedUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
public abstract class CatalogBasedUrlPathAcceptor implements UrlPathAcceptor, Ordered {

    protected final ResourceManager resourceManager;

    protected CatalogBasedUrlPathAcceptor(ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    @Override
    public boolean accept(String referUrl, String path, Packet packet) {
        final long catalogId = packet.getLongField("catalogId");
        Catalog catalog = resourceManager.getCatalog(catalogId);
        return accept(catalog, referUrl, path, packet);
    }

    protected abstract boolean accept(Catalog catalog, String referUrl, String path, Packet packet);



}
