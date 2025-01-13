package com.github.greenfinger.components;

import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: MaxFetchDepthUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
public class MaxFetchDepthUrlPathAcceptor implements UrlPathAcceptor {

    @Override
    public boolean accept(CatalogDetails catalogDetails, String referUrl, String url,
            Packet packet) {
        final int depth = catalogDetails.getMaxFetchDepth();
        if (depth < 0) {
            return true;
        }
        String part = url.replace(referUrl, "");
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
