package com.github.greenfinger.components;

import java.nio.charset.Charset;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: Extractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public interface Extractor extends WebCrawlerComponent {

    default String test(String url, Charset pageEncoding) throws Exception {
        return extractHtml(null, url, url, pageEncoding, null);
    }

    String extractHtml(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, Packet packet) throws Exception;

    default String defaultHtml(CatalogDetails catalogDetails, String referUrl, String url,
            Charset pageEncoding, Packet packet, Throwable e) {
        return "";
    }

}
