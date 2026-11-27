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
package io.micronaut.context.python;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Without native module isolation GraalPy lets only one context of a process load C extensions, so a
 * pooled application with native access is warned at startup instead of failing some requests.
 */
final class PythonPoolNativeModulesTest {

    private static final String NATIVE_ACCESS = GraalPyContextConfiguration.PREFIX + ".allow-native-access";
    private static final String ISOLATE = GraalPyContextConfiguration.PREFIX + ".options." + PythonPool.ISOLATE_NATIVE_MODULES_OPTION;
    private static final String POOL_SIZE = PythonPoolConfiguration.PREFIX + ".size";
    private static final String POOL_ENABLED = PythonPoolConfiguration.PREFIX + ".enabled";

    @Test
    void warnsOnlyWhenPooledContextsMayLoadCExtensionsWithoutIsolation() {
        assertNull(PythonPool.nativeModulesWarning(false, Map.of(), "Linux"));
        assertNull(PythonPool.nativeModulesWarning(true, Map.of(PythonPool.ISOLATE_NATIVE_MODULES_OPTION, "true"), "Linux"));
        // set to false, it is a deliberate choice
        assertNull(PythonPool.nativeModulesWarning(true, Map.of(PythonPool.ISOLATE_NATIVE_MODULES_OPTION, "false"), "Linux"));

        String linux = PythonPool.nativeModulesWarning(true, Map.of(), "Linux");
        assertNotNull(linux);
        assertTrue(linux.contains("only one context"), linux);
        assertTrue(linux.contains("graalpy.context.options.\"python.IsolateNativeModules\" = \"true\""), linux);
        assertTrue(linux.contains("patchelf"), linux);
        assertTrue(linux.contains("micronaut.python.pool.enabled = false"), linux);

        // GraalPy cannot relocate Mach-O libraries: the option would break the first context too
        String mac = PythonPool.nativeModulesWarning(true, Map.of(), "Mac OS X");
        assertNotNull(mac);
        assertTrue(mac.contains("cannot isolate native modules on macOS"), mac);
        assertTrue(mac.contains("micronaut.python.pool.enabled = false"), mac);
        assertTrue(!mac.contains("graalpy.context.options"), mac);
    }

    @Test
    void poolWithNativeAccessWarnsAtStartup() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(NATIVE_ACCESS, true, POOL_SIZE, 2))) {
            assertNotNull(context.getBean(PythonPool.class).nativeModulesWarning());
        }
    }

    @Test
    void noWarningWithIsolationWithoutNativeAccessOrWithoutPool() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(NATIVE_ACCESS, true, POOL_SIZE, 2, ISOLATE, "false"))) {
            assertNull(context.getBean(PythonPool.class).nativeModulesWarning());
        }
        try (ApplicationContext context = ApplicationContext.run(Map.of(POOL_SIZE, 2))) {
            assertNull(context.getBean(PythonPool.class).nativeModulesWarning());
        }
        try (ApplicationContext context = ApplicationContext.run(Map.of(NATIVE_ACCESS, true, POOL_ENABLED, false))) {
            assertNull(context.getBean(PythonPool.class).nativeModulesWarning());
        }
    }
}
