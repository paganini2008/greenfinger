package com.github.greenfinger.components;

import com.github.doodler.common.transmitter.Packet;

/**
 * 
 * @Description: UrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public interface UrlPathAcceptor {

    boolean accept(String referUrl, String path, Packet packet);

}
