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
package io.micronaut.python.annotation.processing.test.visitorintegration

import io.micronaut.python.compiler.PyronautCompiler
import spock.lang.Specification

/**
 * Annotation processor options ({@code -A} javac options) and {@code micronaut.*} system properties reach the
 * type element visitors of a Python compilation through {@code VisitorContext.getOptions()}, as they do for Java.
 */
class VisitorContextOptionsSpec extends Specification {

    private static final String PROCESSOR_OPTION = "micronaut.python.options.spec.processor"
    private static final String SYSTEM_OPTION = "micronaut.python.options.spec.system"

    void setup() {
        OptionsRecordingVisitor.reset()
        System.setProperty(SYSTEM_OPTION, "from-system")
    }

    void cleanup() {
        System.clearProperty(SYSTEM_OPTION)
        // the Java visitor processor mirrors the micronaut.* processor options into the system properties
        System.clearProperty(PROCESSOR_OPTION)
        OptionsRecordingVisitor.reset()
    }

    void "test processor options and micronaut system properties are exposed to visitors"() {
        given:
        def compiler = PyronautCompiler.builder()
            .pythonCode('''
from io.micronaut.python.annotation.processing.test.visitorintegration import RecordOptions


@RecordOptions
class Configured:
    pass
''')
            .options([
                "-A" + PROCESSOR_OPTION + "=from-processor",
                "-Aunrelated.option=ignored"
            ])
            .build()

        when:
        compiler.buildClassLoader()
        def options = OptionsRecordingVisitor.recordedOptions()

        then:
        options[PROCESSOR_OPTION] == "from-processor"
        options[SYSTEM_OPTION] == "from-system"
        !options.containsKey("unrelated.option")
    }
}
