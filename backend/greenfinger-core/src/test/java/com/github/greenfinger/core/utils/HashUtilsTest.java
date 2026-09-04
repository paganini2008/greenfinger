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

package com.github.greenfinger.core.utils;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * 
 * @Description: HashUtilsTest
 * @Author: Fred Feng
 * @Date: 29/08/2026
 * @Version 2.0.0
 */
class HashUtilsTest {

    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    void sha256OfKnownValue() {
        assertThat(HashUtils.sha256("abc")).isEqualTo(ABC_SHA256);
    }

    @Test
    void sha256OfStreamMatchesString() throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8));
        assertThat(HashUtils.sha256(in)).isEqualTo(ABC_SHA256);
    }

    @Test
    void differentInputsHashDifferently() {
        assertThat(HashUtils.sha256("a")).isNotEqualTo(HashUtils.sha256("b"));
    }

    @Test
    void toHexIsLowerCaseAndPadded() {
        assertThat(HashUtils.toHex(new byte[] {0x00, 0x0f, (byte) 0xff})).isEqualTo("000fff");
    }

}
