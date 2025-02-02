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

import com.github.doodler.common.utils.ThreadUtils;

/**
 * 
 * @Description: ThreadWaitType
 * @Author: Fred Feng
 * @Date: 30/12/2024
 * @Version 1.0.0
 */
public enum ThreadWait {

    NONE {

        @Override
        public void doWait(long delay) {}

    },
    RANDOM_SLEEP {

        @Override
        public void doWait(long delay) {
            ThreadUtils.randomSleep(100, delay);
        }

    },

    SLEEP {

        @Override
        public void doWait(long delay) {
            ThreadUtils.sleep(delay);
        }

    };

    public abstract void doWait(long delay);

}
