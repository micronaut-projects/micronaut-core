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
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.util.CollectionUtils;
import org.graalvm.polyglot.Context;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Configuration options for the GraalPy context.
 *
 * @since 5.2.0
 */
@ConfigurationProperties(GraalPyContextConfiguration.PREFIX)
public final class GraalPyContextConfiguration {
    public static final String PREFIX = "graalpy.context";
    /** Package prefixes Python code can always look up: the JDK, Jakarta and the framework itself. */
    private static final List<String> FRAMEWORK_PACKAGES = List.of("java.", "jakarta.", "io.micronaut.");
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

    GraalPyContextConfiguration() {
        // we use experimental features by default so don't warn about them
        builder.option("python.WarnExperimentalFeatures", "false")
        // make sure all logging is routed through GraalPyExceptionHandler
               .option("log.level", "ALL");

    }

    /**
     * @return The builder
     */
    public Context.Builder getBuilder() {
        return builder;
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
     * Empty (the default) exposes every class the application class loader can load.
     *
     * @return The allowed package prefixes
     */
    public List<String> getHostClassLookup() {
        return hostClassLookup;
    }

    /**
     * Restrict the host classes Python code may look up to the given package prefixes. The JDK,
     * Jakarta and the framework's own packages stay visible because generated Python code depends on
     * them.
     *
     * @param hostClassLookup The allowed package prefixes, for example {@code com.example}
     */
    void setHostClassLookup(@Nullable List<String> hostClassLookup) {
        this.hostClassLookup = hostClassLookup == null ? List.of() : List.copyOf(hostClassLookup);
    }

    /**
     * The host class filter for {@link Context.Builder#allowHostClassLookup(Predicate)}.
     *
     * @return The filter
     */
    Predicate<String> hostClassFilter() {
        if (hostClassLookup.isEmpty()) {
            return className -> true;
        }
        // an entry names a package (its classes and subpackages) or one class (and its nested classes)
        List<String> names = hostClassLookup.stream()
            .map(name -> name.endsWith(".") ? name.substring(0, name.length() - 1) : name)
            .toList();
        return className -> FRAMEWORK_PACKAGES.stream().anyMatch(className::startsWith)
            || names.stream().anyMatch(name -> className.equals(name) || className.startsWith(name + '.') || className.startsWith(name + '$'));
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
