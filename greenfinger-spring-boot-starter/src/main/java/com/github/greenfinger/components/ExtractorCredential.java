package com.github.greenfinger.components;

import java.util.Map;

/**
 * 
 * @Description: ExtractorCredential
 * @Author: Fred Feng
 * @Date: 10/01/2025
 * @Version 1.0.0
 */
public interface ExtractorCredential {

    String getUsername();

    String getPassword();

    Map<String, Object> getAdditionalInformation();

}
