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
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The {@code -J} options of a build's javac options, which configure the JVM of a forked compiler. The embedded
 * compiler runs in the application's JVM and javac refuses them there.
 *
 * <p>A system property, {@code -J-Dkey=value}, is what the annotation processors of a forked compiler read, so
 * dropping it would generate different code than the build does. It is set for the compilation and restored when the
 * compilation ends, as Micronaut's processors do with their own options. It cannot become a processor option instead:
 * {@code -A} refuses a key with a hyphen, and a processor may read the property directly. A thread of the running
 * application could read it during the compilation, but no context starts then: the next one starts after the
 * compilation ends. A property the JDK reads only as it starts, {@code file.encoding} for instance, is not set: it
 * would change nothing but what the application reads, and the compilation would still run with the value the JVM
 * started with.</p>
 *
 * <p>An {@code --add-exports} or {@code --add-opens} option, which a compiler plugin or a processor reaching into
 * javac needs, applies only as the JVM starts. The running JVM may have been started with it, or need it.</p>
 *
 * <p>Any other {@code -J} option, a heap size for instance, cannot apply to a JVM already running.</p>
 *
 * @param compilerOptions The options without the {@code -J} ones
 * @param systemProperties The system properties for the compilation
 * @param ignored The {@code -J} options that cannot apply
 * @param startupProperties The {@code -J} options setting a property the JDK reads only as it starts, to a value
 *                          other than the running JVM's
 * @param missingModuleOptions The {@code --add-exports} and {@code --add-opens} options, as JVM arguments, that the
 *                             running JVM was not started with
 */
record JavacJvmOptions(List<String> compilerOptions,
                       Map<String, String> systemProperties,
                       List<String> ignored,
                       List<String> startupProperties,
                       List<String> missingModuleOptions) {

    private static final String JVM_OPTION = "-J";
    private static final String SYSTEM_PROPERTY = "-D";
    private static final String ADD_EXPORTS = "--add-exports";
    private static final String ADD_OPENS = "--add-opens";
    private static final String ALL_UNNAMED = "ALL-UNNAMED";
    /**
     * The properties the JDK reads as it starts, and not after.
     */
    private static final Set<String> STARTUP_PROPERTIES = Set.of(
        "file.encoding", "native.encoding", "sun.jnu.encoding", "stdout.encoding", "stderr.encoding",
        "user.language", "user.country", "user.region", "user.script", "user.variant", "user.timezone",
        "user.dir", "user.home", "java.io.tmpdir", "java.home", "java.class.path", "java.library.path",
        "java.locale.providers", "line.separator", "file.separator", "path.separator"
    );
    private static final Object SYSTEM_PROPERTIES_LOCK = new Object();

    /**
     * Separates the {@code -J} options, comparing them with the running JVM.
     *
     * @param options The options, as the build passes them
     * @return The options, separated
     */
    static JavacJvmOptions of(List<String> options) {
        return of(options, System::getProperty, JavacJvmOptions::inEffect);
    }

    /**
     * Separates the {@code -J} options.
     *
     * @param options The options, as the build passes them
     * @param runningProperties The properties of the running JVM
     * @param moduleOptionInEffect Whether an {@code --add-exports} or {@code --add-opens} JVM argument is in effect
     * @return The options, separated
     */
    static JavacJvmOptions of(List<String> options, UnaryOperator<@Nullable String> runningProperties, Predicate<String> moduleOptionInEffect) {
        List<String> compilerOptions = new ArrayList<>(options.size());
        Map<String, String> systemProperties = new LinkedHashMap<>();
        List<String> ignored = new ArrayList<>();
        List<String> startupProperties = new ArrayList<>();
        List<String> missingModuleOptions = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i);
            if (!option.startsWith(JVM_OPTION)) {
                compilerOptions.add(option);
                continue;
            }
            String jvmOption = option.substring(JVM_OPTION.length());
            String moduleOption = moduleOption(jvmOption);
            if (moduleOption != null) {
                String argument;
                if (jvmOption.length() > moduleOption.length()) {
                    argument = jvmOption.charAt(moduleOption.length()) == '=' ? jvmOption : null;
                } else if (i + 1 < options.size() && options.get(i + 1).startsWith(JVM_OPTION)) {
                    // the value as the next -J option, as a forked JVM reads them
                    argument = moduleOption + "=" + options.get(++i).substring(JVM_OPTION.length());
                } else {
                    argument = null;
                }
                if (argument == null) {
                    ignored.add(option);
                } else if (!moduleOptionInEffect.test(argument)) {
                    missingModuleOptions.add(argument);
                }
                continue;
            }
            String property = jvmOption.startsWith(SYSTEM_PROPERTY) ? jvmOption.substring(SYSTEM_PROPERTY.length()) : "";
            int equals = property.indexOf('=');
            if (property.isEmpty() || equals == 0) {
                ignored.add(option);
                continue;
            }
            String key = equals < 0 ? property : property.substring(0, equals);
            String value = equals < 0 ? "" : property.substring(equals + 1);
            if (!STARTUP_PROPERTIES.contains(key)) {
                systemProperties.put(key, value);
            } else if (!value.equals(runningProperties.apply(key))) {
                startupProperties.add(option);
            }
        }
        return new JavacJvmOptions(compilerOptions, systemProperties, ignored, startupProperties, missingModuleOptions);
    }

    private static @Nullable String moduleOption(String jvmOption) {
        for (String moduleOption : List.of(ADD_EXPORTS, ADD_OPENS)) {
            if (jvmOption.equals(moduleOption) || jvmOption.startsWith(moduleOption + "=")) {
                return moduleOption;
            }
        }
        return null;
    }

    /**
     * Whether the running JVM exports or opens what an {@code --add-exports} or {@code --add-opens} JVM argument
     * names, {@code --add-exports=module/package=target,...}, to each of its targets: to the class path when
     * {@code ALL-UNNAMED}, where the compiler and the processors load.
     *
     * @param argument The JVM argument
     * @return Whether it is in effect
     */
    static boolean inEffect(String argument) {
        boolean opens = argument.startsWith(ADD_OPENS);
        String value = argument.substring(argument.indexOf('=') + 1);
        int slash = value.indexOf('/');
        int equals = value.indexOf('=');
        if (slash <= 0 || equals <= slash + 1) {
            return false;
        }
        Optional<Module> source = ModuleLayer.boot().findModule(value.substring(0, slash));
        if (source.isEmpty()) {
            return false;
        }
        String packageName = value.substring(slash + 1, equals);
        for (String target : value.substring(equals + 1).split(",")) {
            Module module = ALL_UNNAMED.equals(target)
                ? ClassLoader.getSystemClassLoader().getUnnamedModule()
                : ModuleLayer.boot().findModule(target).orElse(null);
            if (module == null || !(opens ? source.get().isOpen(packageName, module) : source.get().isExported(packageName, module))) {
                return false;
            }
        }
        return true;
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
