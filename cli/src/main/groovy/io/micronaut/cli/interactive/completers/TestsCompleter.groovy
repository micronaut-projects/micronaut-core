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

import groovy.transform.CompileStatic
import io.micronaut.cli.io.support.Resource
import io.micronaut.cli.util.CliSettings

import java.util.regex.Pattern

/**
 * A completer that completes the names of the tests in the project
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class TestsCompleter extends ClassNameCompleter {

    private static final Pattern TEST_PATTERN = Pattern.compile('^.*?(Spec|Test|Tests)\\.(groovy|java|kt)$')

    TestsCompleter() {
        super([
            (new File(CliSettings.BASE_DIR, "src/test/groovy")): "**/*.groovy",
            (new File(CliSettings.BASE_DIR, "src/test/java"))  : "**/*.java",
            (new File(CliSettings.BASE_DIR, "src/test/kotlin")): "**/*.kt"
        ])
    }

    @Override
    boolean isValidResource(Resource resource) {
        def fn = resource.filename
        TEST_PATTERN.matcher(fn).matches()
    }
}
