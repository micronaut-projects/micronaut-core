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
package io.micronaut.docs.devmode.tck

import io.micronaut.dev.tck.ReloadHarness
import io.micronaut.dev.tck.ReloadTck
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class GreetingRegistryReloadTest extends Specification {

    @TempDir
    Path tempDir

    void "the registry follows a reload"() {
        expect:
        // tag::harness[]
        try (ReloadHarness harness = ReloadHarness.inDirectory(tempDir)) {
            harness.property("spec.name", "GreetingRegistryReloadTest")
            harness.property("micronaut.server.port", "-1")
            harness.source("example.Greeter", greeter("one"))
            harness.start()
            harness.source("example.Greeter", greeter("two"))
            harness.reload()
            ReloadTck.assertFollowsReload(harness) { context -> context.getBean(GreetingRegistry).current() }
            ReloadTck.assertRetiredGenerationsCollected(harness)
        }
        // end::harness[]
    }

    void "the registry hands out the new greeting"() {
        when:
        ReloadHarness harness = ReloadHarness.inDirectory(tempDir)
        harness.property("spec.name", "GreetingRegistryReloadTest")
        harness.property("micronaut.server.port", "-1")
        harness.source("example.Greeter", greeter("one"))
        String first = harness.start().getBean(GreetingRegistry).current().greet()
        harness.source("example.Greeter", greeter("two"))
        String second = harness.reload().getBean(GreetingRegistry).current().greet()

        then:
        first == "one"
        second == "two"

        cleanup:
        harness?.close()
    }

    // tag::source[]
    private static String greeter(String answer) {
        """\
        package example;

        @jakarta.inject.Singleton
        public class Greeter implements io.micronaut.docs.devmode.tck.Greeting {
            @Override
            public String greet() {
                return "${answer}";
            }
        }
        """.stripIndent()
    }
    // end::source[]
}
