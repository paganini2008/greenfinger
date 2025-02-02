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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.github.greenfinger.components.CountingType;
import com.github.greenfinger.components.ThreadWait;

/**
 * 
 * @Description: CatalogDetails
 * @Author: Fred Feng
 * @Date: 10/01/2025
 * @Version 1.0.0
 */
public interface CatalogDetails {

    Long getId();

    String getName();

    String getUrl();

    String getStartUrl();

    String getCategory();

    List<String> getPathPatterns();

    List<String> getExcludedPathPatterns();

    String getPageEncoding();

    Integer getMaxFetchSize();

    Integer getMaxFetchDepth();

    ThreadWait getThreadWait();

    Long getFetchInterval();

    Long getFetchDuration();

    CountingType getCountingType();

    Integer getMaxRetryCount();

    List<String> getUrlPathAcceptors();

    String getUrlPathFilter();

    String getExtractor();

    String getCredentialHandler();

    Integer getVersion();

    String getRunningState();

    Boolean getIndexed();

    default CatalogCredentials[] getCatalogCredentials() {
        return new CatalogCredentials[0];
    }

    /**
     * 
     * @Description: CatalogCredentials
     * @Author: Fred Feng
     * @Date: 12/01/2025
     * @Version 1.0.0
     */
    interface CatalogCredentials {

        String getUsername();

        String getPassword();

        default Map<String, String> getAdditionalInformation() {
            return Collections.emptyMap();
        }

    }

}
