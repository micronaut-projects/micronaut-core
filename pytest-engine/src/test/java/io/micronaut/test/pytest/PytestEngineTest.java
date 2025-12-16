/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.test.pytest;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineTestKit;

import java.nio.file.Paths;
import java.util.Set;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;

/**
 * Integration test for the PytestTestEngine.
 */
class PytestEngineTest {

    @Test
    void engineCanDiscoverPythonTests() {
        EngineTestKit
            .engine("pytest-engine")
            .selectors(DiscoverySelectors.selectDirectory("src/test/python"))
            .execute()
            .testEvents()
            .debug()
            .assertStatistics(stats -> stats
                .started(5)  // 3 passing tests + 1 failing test
                .succeeded(3)
                .failed(2)
                .skipped(0));
    }

    @Test
    void engineCanDiscoverSpecificTestFile() {
        EngineTestKit
            .engine("pytest-engine")
            .selectors(DiscoverySelectors.selectFile("src/test/python/test_example.py"))
            .execute()
            .testEvents()
            .debug()
            .assertStatistics(stats -> stats
                .started(5)
                .succeeded(3)
                .failed(2)
                .skipped(0));
    }
}
