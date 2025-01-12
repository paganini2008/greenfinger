package com.github.greenfinger.components;

import org.springframework.core.Ordered;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: UrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public interface UrlPathAcceptor extends WebCrawlerComponent, Ordered {

    boolean accept(CatalogDetails catalogDetails, String referUrl, String url, Packet packet);

}
