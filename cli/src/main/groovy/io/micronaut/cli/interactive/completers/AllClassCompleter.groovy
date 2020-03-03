/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.cli.interactive.completers

import io.micronaut.cli.util.CliSettings

/**
 * A completer that completes all classes in the project
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class AllClassCompleter extends ClassNameCompleter {
    AllClassCompleter() {
        super([(new File(CliSettings.BASE_DIR, "src/main/java"))  : "**/*.java",
               (new File(CliSettings.BASE_DIR, "src/main/groovy")): "**/*.groovy",
               (new File(CliSettings.BASE_DIR, "src/main/kotlin")): "**/*.kt"])
    }
}
