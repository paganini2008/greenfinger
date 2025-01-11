package com.github.greenfinger.components;

import org.springframework.core.Ordered;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.WebCrawlerExtractorProperties;
import lombok.RequiredArgsConstructor;

/**
 * 
 * @Description: DepthUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
@RequiredArgsConstructor
public class DepthUrlPathAcceptor implements UrlPathAcceptor, Ordered {

    private final WebCrawlerExtractorProperties config;

    @Override
    public boolean accept(String referUrl, String path, Packet packet) {
        int depth = (Integer) packet.getField("depth", config.getDefaultFetchDepth());
        if (depth < 0) {
            return true;
        }
        String part = path.replace(referUrl, "");
        if (part.charAt(0) == '/') {
            part = part.substring(1);
        }
        int n = 0;
        for (char ch : part.toCharArray()) {
            if (ch == '/') {
                n++;
            }
        }
        return n <= depth;
    }

    @Override
    public int getOrder() {
        return 0;
    }

}
