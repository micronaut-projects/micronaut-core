/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.dev.livereload;

import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.DevRuntimes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DefaultLiveReloadTriggerTest {

    @TempDir
    Path project;

    @Test
    void withoutAServerTheTriggerIsOffAndDoesNothing() throws Exception {
        try (DevRuntime runtime = DevRuntimes.unstarted(project)) {
            DefaultLiveReloadTrigger trigger = new DefaultLiveReloadTrigger(runtime);
            assertFalse(trigger.isEnabled());
            trigger.reload();
            trigger.reload(project.resolve("index.html"));
            trigger.reloadCss(project.resolve("site.css"));
            // a file under no resource root is asked for by its name
            assertEquals("/site.css", runtime.publicPathOf(project.resolve("elsewhere/site.css")));
        }
    }
}
