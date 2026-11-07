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
package io.micronaut.dev.compile;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The {@code -J} options of a build's javac options, which configure the JVM of a forked compiler. The embedded
 * compiler runs in the application's JVM and javac refuses them there.
 *
 * <p>A system property, {@code -J-Dkey=value}, is what the annotation processors of a forked compiler read, so
 * dropping it would generate different code than the build does. It is set for the compilation and restored when the
 * compilation ends, as Micronaut's processors do with their own options. It cannot become a processor option instead:
 * {@code -A} refuses a key with a hyphen, and a processor may read the property directly. A thread of the running
 * application could read it during the compilation, but no context starts then: the next one starts after the
 * compilation ends. Any other {@code -J} option, a heap size for instance, cannot apply to a JVM already running.</p>
 *
 * @param compilerOptions The options without the {@code -J} ones
 * @param systemProperties The system properties for the compilation
 * @param ignored The {@code -J} options that cannot apply
 */
record JavacJvmOptions(List<String> compilerOptions, Map<String, String> systemProperties, List<String> ignored) {

    private static final String JVM_OPTION = "-J";
    private static final String SYSTEM_PROPERTY = "-D";
    private static final Object SYSTEM_PROPERTIES_LOCK = new Object();

    /**
     * Separates the {@code -J} options.
     *
     * @param options The options, as the build passes them
     * @return The options, separated
     */
    static JavacJvmOptions of(List<String> options) {
        List<String> compilerOptions = new ArrayList<>(options.size());
        Map<String, String> systemProperties = new LinkedHashMap<>();
        List<String> ignored = new ArrayList<>();
        for (String option : options) {
            if (!option.startsWith(JVM_OPTION)) {
                compilerOptions.add(option);
                continue;
            }
            String jvmOption = option.substring(JVM_OPTION.length());
            String property = jvmOption.startsWith(SYSTEM_PROPERTY) ? jvmOption.substring(SYSTEM_PROPERTY.length()) : "";
            int equals = property.indexOf('=');
            if (property.isEmpty() || equals == 0) {
                ignored.add(option);
            } else if (equals < 0) {
                systemProperties.put(property, "");
            } else {
                systemProperties.put(property.substring(0, equals), property.substring(equals + 1));
            }
        }
        return new JavacJvmOptions(compilerOptions, systemProperties, ignored);
    }

    /**
     * Runs a compilation with the system properties set, restoring what they were when it ends, however it ends. One
     * compilation runs at a time, so that none sees the properties of another.
     *
     * @param compilation The compilation
     * @param <T> The type of its result
     * @return Its result
     */
    <T> T withSystemProperties(Supplier<T> compilation) {
        synchronized (SYSTEM_PROPERTIES_LOCK) {
            Map<String, @Nullable String> previous = new HashMap<>();
            try {
                systemProperties.forEach((key, value) -> previous.put(key, System.setProperty(key, value)));
                return compilation.get();
            } finally {
                previous.forEach((key, value) -> {
                    if (value == null) {
                        System.clearProperty(key);
                    } else {
                        System.setProperty(key, value);
                    }
                });
            }
        }
    }
}
