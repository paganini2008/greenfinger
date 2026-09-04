/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
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

package com.github.greenfinger.record;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import com.github.greenfinger.core.model.Image;

/**
 * 
 * @Description: ImageRepository
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public interface ImageRepository extends JpaRepository<Image, String> {

    Optional<Image> findByCatalogIdAndVersionAndContentHash(String catalogId, int version,
            String contentHash);

    long countByCatalogIdAndVersion(String catalogId, int version);

    @Modifying
    @Query("delete from Image i where i.catalogId = ?1 and i.version = ?2")
    int deleteByCatalogIdAndVersion(String catalogId, int version);

    int deleteByCatalogId(String catalogId);

}
