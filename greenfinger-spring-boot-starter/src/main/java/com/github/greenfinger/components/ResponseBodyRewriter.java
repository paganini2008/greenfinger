package com.github.greenfinger.components;

import java.nio.charset.Charset;
import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: ResponseBodyRewriter
 * @Author: Fred Feng
 * @Date: 11/01/2025
 * @Version 1.0.0
 */
public interface ResponseBodyRewriter {

    String rewrite(CatalogDetails catalogDetails, String referUrl, String url, Charset pageEncoding,
            String content);

}
