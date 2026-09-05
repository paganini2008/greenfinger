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
import java.util.List;
import com.github.greenfinger.core.model.DeleteLayer;

/**
 * What a delete did, or would have done. Every layer reports separately: the four stores cannot be
 * emptied atomically, so a partial result has to be visible rather than hidden behind one number.
 * 
 * @Description: DeleteReport
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public class DeleteReport {

    private final List<Line> lines = new ArrayList<>();

    public void add(int version, DeleteLayer layer, long count, long bytes, String error) {
        lines.add(new Line(version, layer, count, bytes, error));
    }

    public List<Line> getLines() {
        return lines;
    }

    public boolean hasFailures() {
        return lines.stream().anyMatch(l -> l.error() != null);
    }

    public long total() {
        return lines.stream().mapToLong(Line::count).sum();
    }

    /**
     * @param count rows, documents, points or files, depending on the layer; -1 when the store does
     *        not report a number
     * @param bytes only meaningful for the file layer
     */
    public record Line(int version, DeleteLayer layer, long count, long bytes, String error) {
    }

}
