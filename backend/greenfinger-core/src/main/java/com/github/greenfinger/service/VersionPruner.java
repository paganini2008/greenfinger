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

package com.github.greenfinger.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import com.github.greenfinger.core.catalog.CatalogDetails;
import com.github.greenfinger.core.model.DeleteLayer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the number of retained versions under control.
 *
 * <p>
 * Runs after a crawl has finished and its version has been published, never during one. All four
 * stores keep every version, so ten retained versions means ten complete copies -- of the files, of
 * the index and of the vectors alike -- and without this the disk would grow without limit.
 *
 * <p>
 * The version being written and the version search is serving are never candidates. Beyond that the
 * oldest go first, except that versions with no data at all -- a run that was interrupted and never
 * resumed -- are taken before any complete one, since they occupy a slot while being of no use.
 * 
 * @Description: VersionPruner
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class VersionPruner {

    private final DeletionService deletionService;

    public DeleteReport prune(CatalogDetails catalogDetails) {
        int keep = catalogDetails.getMaxVersions() != null ? catalogDetails.getMaxVersions() : 0;
        if (keep <= 0) {
            return new DeleteReport();
        }
        List<Integer> versions = new ArrayList<>(deletionService.versionsOf(catalogDetails));
        versions.removeIf(v -> v.equals(catalogDetails.getVersion())
                || v.equals(catalogDetails.getSearchVersion()));

        int excess = versions.size() + 2 - keep;
        if (excess <= 0) {
            return new DeleteReport();
        }
        List<Integer> doomed = versions.stream().sorted().limit(excess).toList();
        log.info("Pruning version(s) {} of '{}', keeping {}", doomed, catalogDetails.getName(),
                keep);
        return deletionService.delete(catalogDetails, doomed, EnumSet.allOf(DeleteLayer.class),
                false, false);
    }

}
