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

import com.github.doodler.common.transmitter.Packet;
import com.github.greenfinger.CatalogDetails;

/**
 * 
 * @Description: MaxFetchDepthUrlPathAcceptor
 * @Author: Fred Feng
 * @Date: 31/12/2024
 * @Version 1.0.0
 */
public class MaxFetchDepthUrlPathAcceptor implements UrlPathAcceptor {

    @Override
    public boolean accept(CatalogDetails catalogDetails, String referUrl, String url,
            Packet packet) {
        final int depth = catalogDetails.getMaxFetchDepth();
        if (depth < 0) {
            return true;
        }
        String part = url.replace(referUrl, "");
        if (part.charAt(0) == '/') {
            part = part.substring(1);
        }
        int n = 0;
        for (char ch : part.toCharArray()) {
            if (ch == '/') {
                n++;
            }
        }
        return n <= depth;
    }

    @Override
    public int getOrder() {
        return 0;
    }

}
