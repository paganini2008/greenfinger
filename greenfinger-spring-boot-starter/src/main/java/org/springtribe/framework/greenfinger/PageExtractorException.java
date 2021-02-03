package org.springtribe.framework.greenfinger;

import org.springframework.http.HttpStatus;

/**
 * 
 * PageExtractorException
 *
 * @author Jimmy Hoff
 * 
 * @since 1.0
 */
public class PageExtractorException extends RuntimeException {

	private static final long serialVersionUID = 4816595505153970862L;
	private static final String msgFormat = "%s [%s: %s]";

	public PageExtractorException(String url, HttpStatus httpStatus) {
		super(String.format(msgFormat, url, httpStatus.value(), httpStatus.getReasonPhrase()));
		this.httpStatus = httpStatus;
	}

	private final HttpStatus httpStatus;

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}

}
