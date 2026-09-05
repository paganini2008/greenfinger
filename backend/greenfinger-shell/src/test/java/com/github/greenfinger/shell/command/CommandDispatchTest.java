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

package com.github.greenfinger.shell.command;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.github.greenfinger.shell.CommandLine;
import com.github.greenfinger.core.model.DeleteLayer;
import com.github.greenfinger.core.model.OutputType;

/**
 * The parsing the one-line form depends on. The commands themselves need a database and a running
 * site, and are covered by the starter's integration tests.
 * 
 * @Description: CommandDispatchTest
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
class CommandDispatchTest {

    @Test
    void readsACommandAndItsOptions() {
        CommandLine line = CommandLine
                .parse(new String[] {"crawl", "--url", "https://example.com", "--max-size=100"});

        assertThat(line.hasCommand()).isTrue();
        assertThat(line.getCommand()).isEqualTo("crawl");
        assertThat(line.getOptions().get("url", null)).isEqualTo("https://example.com");
        assertThat(line.getOptions().getInt("maxSize", 0)).isEqualTo(100);
    }

    @Test
    void noArgumentsMeansTheInteractivePrompt() {
        assertThat(CommandLine.parse(new String[0]).hasCommand()).isFalse();
    }

    @Test
    void layersParseIntoTheDeletionOrder() {
        assertThat(DeleteLayer.parse("all")).containsExactly(DeleteLayer.VECTOR,
                DeleteLayer.INDEX, DeleteLayer.FILE, DeleteLayer.DB);
        assertThat(DeleteLayer.parse("file,vector")).containsExactly(DeleteLayer.VECTOR,
                DeleteLayer.FILE);
        // "+" is what --layers documents, and what somebody copying the help will type
        assertThat(DeleteLayer.parse("index+vector")).containsExactly(DeleteLayer.VECTOR,
                DeleteLayer.INDEX);
        assertThat(DeleteLayer.parse(null)).hasSize(4);
    }

    @Test
    void outputTypesParseFromTheCommandLineForm() {
        assertThat(OutputType.parse("index,vector")).contains(OutputType.FILE, OutputType.INDEX,
                OutputType.VECTOR);
    }


    @Test
    @DisplayName("two words join into one command name, so 'models pull' reaches models-pull")
    void twoWordModelCommandsParse() {
        CommandLine line = CommandLine.parse(new String[] {"models", "pull", "--layers", "text"});

        assertThat(line.getCommand()).isEqualTo("models-pull");
        assertThat(line.getPrimaryCommand()).isEqualTo("models");
        assertThat(line.getOptions().get("layers", null)).isEqualTo("text");
    }

    @Test
    void barePluralIsTheListing() {
        assertThat(CommandLine.parse(new String[] {"models"}).getCommand()).isEqualTo("models");
    }

}
