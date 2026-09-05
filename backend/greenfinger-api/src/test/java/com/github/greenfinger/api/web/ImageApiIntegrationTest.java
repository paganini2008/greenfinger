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

package com.github.greenfinger.api.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Handing back a picture the crawl saved.
 *
 * <p>
 * The path is supplied by the caller, so half of what is asserted here is what the endpoint
 * refuses. The other half is that it actually serves the bytes -- without it the picture search
 * has nothing to show, which is the whole reason the endpoint exists.
 *
 * @Description: ImageApiIntegrationTest
 * @Author: Fred Feng
 * @Date: 31/08/2026
 * @Version 2.0.0
 */
@SpringBootTest(classes = WebTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "greenfinger.output.file.directory=${java.io.tmpdir}/gf-image/data",
        "greenfinger.frontier-directory=${java.io.tmpdir}/gf-image/frontier",
        "greenfinger.dedup.url.directory=${java.io.tmpdir}/gf-image/url",
        "greenfinger.dedup.content.directory=${java.io.tmpdir}/gf-image/content",
        "spring.datasource.url=jdbc:h2:mem:greenfinger-image;DB_CLOSE_DELAY=-1",
        "greenfinger.security.enabled=false"})
class ImageApiIntegrationTest {

    /** A one pixel gif. Small enough to inline, and real enough to be served as bytes. */
    private static final byte[] GIF = {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, (byte) 0x80, 0, 0,
            0, 0, 0, (byte) 0xff, (byte) 0xff, (byte) 0xff, ',', 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2,
            'D', 1, 0, ';'};

    private static final String IMAGE_ID = "0197c0de-1234-5678-9abc-def012345678";
    private static final String IMAGE_PATH =
            "books/v0/images/01/97/" + IMAGE_ID + ".gif";

    @Autowired
    private MockMvc mockMvc;

    private Path dataDirectory;

    @BeforeEach
    void setUp() throws Exception {
        dataDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "gf-image", "data");
        Path file = dataDirectory.resolve(IMAGE_PATH);
        Files.createDirectories(file.getParent());
        Files.write(file, GIF);
    }

    @Test
    @DisplayName("serves the archived bytes, with the type its extension says and a long cache")
    void servesAStoredImage() throws Exception {
        mockMvc.perform(get("/v2/image").param("path", IMAGE_PATH)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_GIF))
                .andExpect(content().bytes(GIF))
                // the id in the name is a hash of the bytes, so the file can never change under it
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("immutable")))
                // the bytes came off somebody else's site and are served from our origin
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("sandbox")));
    }

    @Test
    @DisplayName("a version that has been deleted leaves vectors pointing at nothing: 404, not 500")
    void reportsAMissingImage() throws Exception {
        mockMvc.perform(get("/v2/image").param("path",
                "books/v0/images/ff/ff/0197c0de-0000-0000-0000-000000000000.gif"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the path is checked against the layout, not scanned for tricks")
    void refusesAnythingThatIsNotAnImagePath() throws Exception {
        for (String path : new String[] {"../../../etc/passwd", "/etc/passwd",
                "books/v0/../../../etc/passwd", "books/v0/images/01/97/" + IMAGE_ID + ".jsp",
                "books/v0/images/01/97/" + IMAGE_ID + ".html",
                "books/v0/pages/01/97/" + IMAGE_ID + ".html",
                "books%2Fv0%2F..%2F..%2Fetc%2Fpasswd", "books/v0/images/01/97/not-a-uuid.gif"}) {
            int status = mockMvc.perform(get("/v2/image").param("path", path)).andReturn()
                    .getResponse().getStatus();
            org.assertj.core.api.Assertions.assertThat(status).as("path '%s'", path)
                    .isEqualTo(409);
        }
    }

}
