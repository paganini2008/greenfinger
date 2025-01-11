package com.github.greenfinger.components;

import java.nio.charset.Charset;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.model.Catalog;

/**
 * 
 * @Description: Extractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public interface Extractor extends WebCrawlerComponent {

    String extractHtml(Catalog catalog, String referUrl, String url, Charset pageEncoding,
            Packet packet) throws Exception;

    default String defaultHtml(Catalog catalog, String referUrl, String url, Charset pageEncoding,
            Packet packet, Throwable e) {
        return "";
    }

}
