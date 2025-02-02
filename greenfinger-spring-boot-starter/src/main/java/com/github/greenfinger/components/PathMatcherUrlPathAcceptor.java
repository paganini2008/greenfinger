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

package com.github.greenfinger.components;

import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: PathMatcherUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public class PathMatcherUrlPathAcceptor implements UrlPathAcceptor {

    private final PathMatcher pathMather = new AntPathMatcher();

    @Override
    public boolean accept(CatalogDetails catalogDetails, String referUrl, String url,
            Packet packet) {
        List<String> pathPatterns = catalogDetails.getExcludedPathPatterns();
        for (String pathPattern : pathPatterns) {
            if (pathMather.match(pathPattern, url)) {
                return false;
            }
        }
        pathPatterns = catalogDetails.getPathPatterns();
        for (String pathPattern : pathPatterns) {
            if (pathMather.match(pathPattern, url)) {
                return true;
            }
        }
        if (CollectionUtils.isEmpty(pathPatterns)) {
            return url.startsWith(referUrl);
        }
        return false;
    }

    @Override
    public int getOrder() {
        return 1;
    }

    public static void main(String[] args) {
        PathMatcher pathMather = new AntPathMatcher();
        final String pattern = "https://**.tuniu.**/**";
        System.out.println(pathMather.match(pattern, "https://sina.tuniu.com/a/b"));
    }

}
