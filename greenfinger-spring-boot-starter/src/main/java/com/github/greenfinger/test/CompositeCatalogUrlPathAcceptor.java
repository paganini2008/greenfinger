
package com.github.greenfinger.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.collections4.CollectionUtils;
import com.github.doodler.common.transmitter.Packet;
import com.github.doodler.common.utils.MapUtils;
import com.github.greenfinger.ResourceManager;
import com.github.greenfinger.UrlPathAcceptor;
import com.github.greenfinger.model.Catalog;

/**
 * 
 * @Description: CompositeCatalogUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
public class CompositeCatalogUrlPathAcceptor extends CatalogBasedUrlPathAcceptor {

    private final Map<Long, List<UrlPathAcceptor>> acceptorMapper = new ConcurrentHashMap<>();

    public CompositeCatalogUrlPathAcceptor(ResourceManager resourceManager) {
        super(resourceManager);
    }

    public void addUrlPathAcceptor(long catalogId, UrlPathAcceptor urlPathAcceptor) {
        if (urlPathAcceptor != null) {
            MapUtils.getOrCreate(acceptorMapper, catalogId, ArrayList::new).add(urlPathAcceptor);
        }
    }

    public void removeUrlPathAcceptor(long catalogId, UrlPathAcceptor urlPathAcceptor) {
        if (urlPathAcceptor != null && acceptorMapper.containsKey(catalogId)) {
            acceptorMapper.get(catalogId).remove(urlPathAcceptor);
        }
    }

    @Override
    public boolean accept(Catalog catalog, String refer, String path, Packet packet) {
        List<UrlPathAcceptor> acceptors = acceptorMapper.get(catalog.getId());
        if (CollectionUtils.isNotEmpty(acceptors)) {
            for (UrlPathAcceptor acceptor : acceptors) {
                if (!acceptor.accept(refer, path, packet)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public int getOrder() {
        return 100;
    }

}
