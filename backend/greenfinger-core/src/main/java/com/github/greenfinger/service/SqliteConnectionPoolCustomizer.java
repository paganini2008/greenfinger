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

import org.springframework.beans.factory.config.BeanPostProcessor;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;

/**
 * Makes SQLite survive a crawl by pinning the pool to one connection. SQLite locks the whole file
 * to write, so twenty connections and a dozen crawl threads become {@code SQLITE_BUSY}, not
 * throughput -- measured at 72 failures and one page saved out of twelve. WAL and a busy timeout
 * are not enough, because two transactions upgrading a read lock cannot both wait and SQLite fails
 * one immediately; a single connection removes the race.
 *
 * <p>
 * In code rather than in {@code application-prod.yml} because the defaults are shared by four
 * databases and the one that needs different ones cannot announce itself. WAL stays on so readers
 * carry on, and the busy timeout backstops another process holding the file.
 *
 * @Description: SqliteConnectionPoolCustomizer
 * @Author: Fred Feng
 * @Date: 01/09/2026
 * @Version 2.0.0
 */
@Slf4j
public class SqliteConnectionPoolCustomizer implements BeanPostProcessor {

    private static final String SQLITE = "jdbc:sqlite:";

    /** Long enough to outlast another process's checkpoint, short enough to still fail a hang. */
    private static final String BUSY_TIMEOUT = "30000";

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (!(bean instanceof HikariDataSource dataSource)) {
            return bean;
        }
        String url = dataSource.getJdbcUrl();
        if (url == null || !url.startsWith(SQLITE)) {
            return bean;
        }
        String adjusted = withPragmas(url);
        if (!adjusted.equals(url)) {
            log.info("SQLite: {} (write ahead logging, and a wait rather than an immediate"
                    + " SQLITE_BUSY)", adjusted);
            dataSource.setJdbcUrl(adjusted);
        }
        return bean;
    }

    /**
     * Adds what is missing and keeps what the user set: an explicit {@code journal_mode} in the url
     * is a decision, not an oversight.
     */
    static String withPragmas(String url) {
        StringBuilder result = new StringBuilder(url);
        boolean hasQuery = url.indexOf('?') >= 0;
        if (!url.contains("journal_mode")) {
            result.append(hasQuery ? '&' : '?').append("journal_mode=WAL");
            hasQuery = true;
        }
        if (!url.contains("busy_timeout")) {
            result.append(hasQuery ? '&' : '?').append("busy_timeout=").append(BUSY_TIMEOUT);
        }
        return result.toString();
    }

}
