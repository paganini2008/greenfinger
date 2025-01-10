package com.github.greenfinger.components;

import java.nio.charset.Charset;
import com.github.doodler.common.transmitter.Packet;

/**
 * 
 * @Description: Extractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public interface Extractor {

    String extractHtml(String referUrl, String url, Charset pageEncoding, Packet packet)
            throws Exception;

    default String defaultPage(String referUrl, String url, Charset pageEncoding, Packet packet,
            Throwable e) {
        return "";
    }

    default String getName() {
        return "";
    }

}
