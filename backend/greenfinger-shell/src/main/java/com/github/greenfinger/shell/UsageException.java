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

package com.github.greenfinger.shell;

import com.github.greenfinger.core.WebCrawlerException;

/**
 * The command cannot run as it was typed: something required is missing, or a value is not one of
 * the ones that exist.
 *
 * <p>
 * These used to be printed where they were found and the command returned normally, which read
 * well enough for a person but told a script nothing -- {@code delete --catalog x} with no version
 * selected exited 0. Raising it instead puts the exit code and the message in one place
 * ({@link OneShotCommandRunner}), and the hint lines survive: the first line of the message is the
 * problem, the rest is what to type instead.
 * 
 * @Description: UsageException
 * @Author: Fred Feng
 * @Date: 02/09/2026
 * @Version 2.0.0
 */
public class UsageException extends WebCrawlerException {

    private static final long serialVersionUID = 1L;

    public UsageException(String problem, String... hints) {
        super(hints.length == 0 ? problem : problem + System.lineSeparator()
                + String.join(System.lineSeparator(), hints));
    }

}
