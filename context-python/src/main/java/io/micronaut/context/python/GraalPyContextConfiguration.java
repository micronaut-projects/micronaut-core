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
import jakarta.inject.Inject;
import org.graalvm.polyglot.Context;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Configuration options for the GraalPy context.
 *
 * @since 5.2.0
 */
@ConfigurationProperties(GraalPyContextConfiguration.PREFIX)
public final class GraalPyContextConfiguration {
    public static final String PREFIX = "graalpy.context";
    private static final Logger LOG = LoggerFactory.getLogger(GraalPyContextFactory.class);

    @ConfigurationBuilder(prefixes = "", excludes = {
        "out",
        "in",
        "err",
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
    Context.Builder builder = Context.newBuilder();
    private Map<String, String> options = Map.of();

    GraalPyContextConfiguration() {
        // we use experimental features by default so don't warn about them
        builder.option("python.WarnExperimentalFeatures", "false");
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
