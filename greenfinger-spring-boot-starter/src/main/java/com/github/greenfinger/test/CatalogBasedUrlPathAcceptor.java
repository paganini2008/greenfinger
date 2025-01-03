package com.github.greenfinger.test;

import org.springframework.core.Ordered;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.ResourceManager;
import com.github.greenfinger.UrlPathAcceptor;
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
    public boolean accept(String refer, String path, Packet packet) {
        final long catalogId = packet.getLongField("catalogId");
        Catalog catalog = resourceManager.getCatalog(catalogId);
        return accept(catalog, refer, path, packet);
    }

    protected abstract boolean accept(Catalog catalog, String refer, String path, Packet packet);



}
