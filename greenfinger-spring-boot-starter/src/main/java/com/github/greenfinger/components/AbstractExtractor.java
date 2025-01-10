package com.github.greenfinger.components;

import java.nio.charset.Charset;
import com.github.doodler.common.transmitter.Packet;

/**
 * 
 * @Description: AbstractExtractor
 * @Author: Fred Feng
 * @Date: 10/01/2025
 * @Version 1.0.0
 */
public abstract class AbstractExtractor implements Extractor {

    @Override
    public String extractHtml(String referUrl, String url, Charset pageEncoding, Packet packet)
            throws Exception {
        String html = requestUrl(referUrl, url, pageEncoding, packet);
        return filterContent(html);
    }

    protected abstract String requestUrl(String referUrl, String url, Charset pageEncoding,
            Packet packet) throws Exception;

    protected String filterContent(String html) {
        return html;
    }

}
