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

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Inject;
import org.graalvm.polyglot.Context;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Configuration options for the GraalPy context.
 *
 * @since 5.2.0
 */
@ConfigurationProperties(GraalPyContextConfiguration.PREFIX)
public final class GraalPyContextConfiguration {
    public static final String PREFIX = "graalpy.context";
    static final String LOG_LEVEL_OPTION = "log.level";
    static final String ROOT_LOG_LEVEL_PROPERTY = "logger.levels.root";
    /** Package prefixes Python code can always look up: the JDK, Jakarta and the framework itself. */
    private static final List<String> FRAMEWORK_PACKAGES = List.of("java.", "jakarta.", "io.micronaut.");
    /** Primitive type names, always visible so that primitive arrays such as {@code byte[]} are. */
    private static final Set<String> PRIMITIVE_TYPES = Set.of("boolean", "byte", "char", "short", "int", "long", "float", "double", "void");
    /** The JVM descriptor codes of the primitive array element types, as in {@code [B}. */
    private static final String PRIMITIVE_DESCRIPTORS = "ZBCSIJFD";
    private static final Logger LOG = LoggerFactory.getLogger(GraalPyContextFactory.class);

    @ConfigurationBuilder(prefixes = "", excludes = {
        "out",
        "in",
        "err",
        "options",
        "environment",
        "exceptionHandler",
        "messageTransport",
        "sharedEngine",
        "hostClassFilter",
        "polyglotAccess",
        "hostAccess",
        "ioAccess",
        "customFileSystem",
        "customLogHandler",
        "processHandler",
        "environmentAccess",
        "hostClassLoader",
        "apply"
    })
    Context.Builder builder = Context.newBuilder(
        GraalPyContextCustomizers.languages(GraalPyContextCustomizers.currentClassLoader())
    );
    private Map<String, String> options = Map.of();
    private List<String> hostClassLookup = List.of();
    private String polyglotLogLevel = GraalPySlf4jLogHandler.polyglotRootLevel();

    GraalPyContextConfiguration() {
        // we use experimental features by default so don't warn about them
        builder.option("python.WarnExperimentalFeatures", "false")
            // Avoid creating records that the SLF4J backend will discard.
            .option(LOG_LEVEL_OPTION, polyglotLogLevel);
    }

    /**
     * @return The builder
     */
    public Context.Builder getBuilder() {
        return builder;
    }

    @Inject
    void configureRootLogLevel(@Property(name = ROOT_LOG_LEVEL_PROPERTY) @Nullable String rootLogLevel) {
        String configuredLevel = GraalPySlf4jLogHandler.polyglotLevel(rootLogLevel);
        if (configuredLevel != null && !options.containsKey(LOG_LEVEL_OPTION)) {
            builder.option(LOG_LEVEL_OPTION, configuredLevel);
            polyglotLogLevel = configuredLevel;
        }
    }

    String polyglotLogLevel() {
        return options.getOrDefault(LOG_LEVEL_OPTION, polyglotLogLevel);
    }

    /**
     * The configured options.
     * @return The options
     */
    public Map<String, String> getOptions() {
        return options;
    }

    /**
     * Sets the engine options.
     *
     * @param options The options.
     */
    void setOptions(@MapFormat(keyFormat = StringConvention.RAW, transformation = MapFormat.MapTransformation.FLAT) @Nullable Map<String, String> options) {
        if (options != null) {
            if (CollectionUtils.isNotEmpty(options)) {
                LOG.debug("Using GraalPy context options {}", options);
                builder.options(options);
                this.options = options;
            }
        }
    }

    /**
     * The package prefixes Python code may look up through {@code java.type} and {@code import}.
     * <p>
     * Empty, the default, means no restriction: every class the application class loader can load is
     * visible, whatever its package. The list is an opt-in hardening knob for applications that run
     * Python code they trust less than their Java code; an application that sets it must list its own
     * packages (the generated Python code looks up the application's Java classes by name) and every
     * library package its Python code touches.
     *
     * @return The allowed package prefixes, empty for no restriction
     */
    public List<String> getHostClassLookup() {
        return hostClassLookup;
    }

    /**
     * Restrict the host classes Python code may look up to the given package prefixes. The JDK,
     * Jakarta and the framework's own packages stay visible because generated Python code depends on
     * them. Not setting the property, or setting it empty, keeps every class visible.
     *
     * @param hostClassLookup The allowed package prefixes, for example {@code com.example}
     */
    void setHostClassLookup(@Nullable List<String> hostClassLookup) {
        this.hostClassLookup = hostClassLookup == null ? List.of() : List.copyOf(hostClassLookup);
    }

    /**
     * The host class filter for {@link Context.Builder#allowHostClassLookup(Predicate)}.
     * <p>
     * With {@link #getHostClassLookup()} set, a name is visible when it is in the JDK, Jakarta or
     * framework packages, or when an entry names its package (or a parent package) or the class
     * itself (or its enclosing class). Primitive types are always visible, and an array is visible
     * when its element type is: Truffle checks {@code byte[][]} and then each component,
     * {@code byte[]} and {@code byte}, and the JVM descriptor form ({@code [B},
     * {@code [Lcom.example.Foo;}) matches the same way.
     *
     * @return The filter
     */
    Predicate<String> hostClassFilter() {
        if (hostClassLookup.isEmpty()) {
            // the default: no restriction, user packages included
            return className -> true;
        }
        // an entry names a package (its classes and subpackages) or one class (and its nested classes)
        List<String> names = hostClassLookup.stream()
            .map(name -> name.endsWith(".") ? name.substring(0, name.length() - 1) : name)
            .toList();
        Predicate<String> classFilter = className -> FRAMEWORK_PACKAGES.stream().anyMatch(className::startsWith)
            || names.stream().anyMatch(name -> className.equals(name) || className.startsWith(name + '.') || className.startsWith(name + '$'));
        return className -> isVisible(className, classFilter);
    }

    /**
     * Whether a host class name is visible, matching an array by its element type.
     *
     * @param className The class name Truffle looks up, for example {@code byte[]} or {@code [B}
     * @param classFilter The filter for a non-array, non-primitive class name
     * @return Whether the class is visible
     */
    private static boolean isVisible(String className, Predicate<String> classFilter) {
        String name = className;
        while (name.endsWith("[]")) {
            name = name.substring(0, name.length() - 2);
        }
        if (name.startsWith("[")) {
            // a JVM descriptor: [B, [[I, [Lcom.example.Foo;
            String element = name.substring(name.lastIndexOf('[') + 1);
            if (element.length() == 1) {
                return PRIMITIVE_DESCRIPTORS.indexOf(element.charAt(0)) >= 0;
            }
            if (element.length() < 3 || element.charAt(0) != 'L' || !element.endsWith(";")) {
                return false;
            }
            name = element.substring(1, element.length() - 1);
        }
        return PRIMITIVE_TYPES.contains(name) || classFilter.test(name);
    }

    /**
     * Sets the engine environment.
     *
     * @param env The env.
     */
    void setEnvironment(@MapFormat(keyFormat = StringConvention.RAW, transformation = MapFormat.MapTransformation.FLAT) @Nullable Map<String, String> env) {
        if (env != null) {
            if (CollectionUtils.isNotEmpty(env)) {
                LOG.debug("Using GraalPy context env {}", env);
                builder.environment(env);
            }
        }
    }
}
