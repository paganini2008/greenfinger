
package com.github.greenfinger.components;

import org.springframework.http.HttpStatus;
import com.github.greenfinger.WebCrawlerException;

/**
 * 
 * @Description: ExtractorException
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public class ExtractorException extends WebCrawlerException {

    private static final long serialVersionUID = 4816595505153970862L;

    public ExtractorException(String url, HttpStatus httpStatus) {
        super(String.format("%s [%s: %s]", url, httpStatus.value(), httpStatus.getReasonPhrase()));
        this.httpStatus = httpStatus;
    }

    private final HttpStatus httpStatus;

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

}
