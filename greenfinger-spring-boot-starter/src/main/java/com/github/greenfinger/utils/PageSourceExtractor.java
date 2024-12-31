package com.github.greenfinger.utils;

import java.nio.charset.Charset;
import com.github.doodler.common.transmitter.Packet;

/**
 * 
 * @Description: PageSourceExtractor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public interface PageSourceExtractor {

    String extractHtml(String refer, String url, Charset pageEncoding, Packet packet)
            throws Exception;

    default String defaultPage(String refer, String url, Charset pageEncoding, Packet packet,
            Throwable e) {
        return "";
    }

}
