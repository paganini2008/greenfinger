package com.github.greenfinger;

import static com.github.doodler.common.Constants.REQUEST_HEADER_TIMESTAMP;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.doodler.common.ApiResult;
import com.github.doodler.common.context.HttpRequestContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @Description: GlobalAccessDeniedHandler
 * @Author: Fred Feng
 * @Date: 30/01/2025
 * @Version 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class GlobalAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException e) throws IOException, ServletException {
        if (log.isErrorEnabled()) {
            log.error(e.getMessage(), e);
        }
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResult<String> result = ApiResult.failed(e.getMessage());
        result.setRequestPath(request.getRequestURI());
        String timestamp = HttpRequestContextHolder.getHeader(REQUEST_HEADER_TIMESTAMP);
        if (StringUtils.isNotBlank(timestamp)) {
            result.setElapsed(System.currentTimeMillis() - Long.parseLong(timestamp));
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

}
