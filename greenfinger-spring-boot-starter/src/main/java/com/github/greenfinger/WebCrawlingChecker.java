/*
 * Copyright 2017-2025 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger;

import java.lang.reflect.Parameter;
import org.apache.commons.lang3.ArrayUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 
 * @Description: WebCrawlingChecker
 * @Author: Fred Feng
 * @Date: 30/01/2025
 * @Version 1.0.0
 */
@Aspect
@Component
public class WebCrawlingChecker {

    @Autowired
    private WebCrawlerSemaphore semaphore;

    @Autowired
    private CatalogDetailsService catalogDetailsService;

    @Pointcut("execution(public * *(..))")
    public void signature() {}

    @Around("signature() && @annotation(webCrawling)")
    public Object arround(ProceedingJoinPoint pjp, WebCrawling webCrawling) throws Throwable {
        CatalogDetails catalogDetails = catalogDetailsService.loadRunningCatalogDetails();
        if (catalogDetails == null) {
            return pjp.proceed();
        }
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        long catalogId = findCatalogId(parameters, pjp.getArgs());
        if (catalogId != -1 && catalogDetails.getId().equals(catalogId) && semaphore.isOccupied()) {
            throw new WebCrawlerRunningException("Current catalog is running a web crawler job");
        }
        return pjp.proceed();
    }

    private long findCatalogId(Parameter[] parameters, Object[] args) {
        if (ArrayUtils.isEmpty(parameters)) {
            return -1;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equalsIgnoreCase("catalogId")
                    && (parameters[i].getType() == long.class
                            || parameters[i].getType() == Long.class)) {
                return (Long) args[i];
            }
        }
        return -1;
    }
}
