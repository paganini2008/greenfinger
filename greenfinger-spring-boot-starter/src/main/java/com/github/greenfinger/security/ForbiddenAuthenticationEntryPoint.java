package com.github.greenfinger.security;

import static com.github.doodler.common.Constants.REQUEST_HEADER_TIMESTAMP;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.doodler.common.ApiResult;
import com.github.doodler.common.context.HttpRequestContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: ForbiddenAuthenticationEntryPoint
 * @Author: Fred Feng
 * @Date: 30/01/2025
 * @Version 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class ForbiddenAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    @SneakyThrows
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException e) {
        if (log.isErrorEnabled()) {
            log.error(e.getMessage(), e);
        }
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResult<Object> result = ApiResult.failed(e.getMessage());
        result.setRequestPath(request.getRequestURI());
        String timestamp = HttpRequestContextHolder.getHeader(REQUEST_HEADER_TIMESTAMP);
        if (StringUtils.isNotBlank(timestamp)) {
            result.setElapsed(System.currentTimeMillis() - Long.parseLong(timestamp));
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        PrintWriter printWriter = response.getWriter();
        printWriter.append(objectMapper.writeValueAsString(result));
    }

}
