package com.github.greenfinger.security;

import static com.github.doodler.common.Constants.REQUEST_HEADER_TIMESTAMP;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.github.doodler.common.ApiResult;
import com.github.doodler.common.context.HttpRequestContextHolder;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: AuthenticationExceptionHandler
 * @Author: Fred Feng
 * @Date: 30/01/2025
 * @Version 1.0.0
 */
@Slf4j
@Order(90)
@RestControllerAdvice
public class AuthenticationExceptionHandler {

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResult<?>> handleAuthenticationException(HttpServletRequest request,
            AuthenticationException e) {

        if (log.isErrorEnabled()) {
            log.error(e.getMessage(), e);
        }
        ApiResult<Object> result = ApiResult.failed(e.getMessage());
        result.setRequestPath(request.getRequestURI());
        String timestamp = HttpRequestContextHolder.getHeader(REQUEST_HEADER_TIMESTAMP);
        if (StringUtils.isNotBlank(timestamp)) {
            result.setElapsed(System.currentTimeMillis() - Long.parseLong(timestamp));
        }
        return new ResponseEntity<>(result, HttpStatus.UNAUTHORIZED);
    }

}
