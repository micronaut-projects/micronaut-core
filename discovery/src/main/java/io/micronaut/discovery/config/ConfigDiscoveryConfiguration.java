/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.discovery.config;

import io.micronaut.core.util.Toggleable;

import java.util.Optional;

/**
 * Abstract class for common configuration discovery settings.
 *
 * @author graemerocher
 * @since 1.0
 */
public abstract class ConfigDiscoveryConfiguration implements Toggleable {

    /**
     * The prefix to use for all Consul client config settings.
     */
    public static final String PREFIX = "config";

    /**
     * The default path.
     */
    public static final String DEFAULT_PATH = PREFIX + "/";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    private boolean enabled = DEFAULT_ENABLED;
    private String path;
    private Format format = Format.NATIVE;

    /**
     * @return Is distributed configuration enabled. True if it is.
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled Enable the distributed configuration
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The path where the configuration is stored
     */
    public Optional<String> getPath() {
        return Optional.ofNullable(path);
    }

    /**
     * @param path The path to store the configuration
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * @return The configuration format
     */
    public Format getFormat() {
        return format;
    }

    /**
     * @param format The configuration format
     */
    public void setFormat(Format format) {
        if (format != null) {
            this.format = format;
        }
    }

    /**
     * The format the configuration is stored in.
     */
    public enum Format {

        /**
         * Stored in YAML format.
         */
        YAML,

        /**
         * Stored in JSON format.
         */
        JSON,

        /**
         * Stored in Java properties file format.
         */
        PROPERTIES,

        /**
         * Stored in the native format provided by the configuration server.
         */
        NATIVE,

        /**
         * Each value in the configuration server represents the name of a file and the contents of the file.
         * Useful when using solutions such as git2consul.
         */
        FILE
    }
}
