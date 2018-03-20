/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.discovery.config;

import java.util.Optional;

/**
 * Abstract class for common configuration discovery settings
 *
 * @author graemerocher
 * @since 1.0
 */
public abstract class ConfigDiscoveryConfiguration {

    public static final String PREFIX = "config";
    public static final String DEFAULT_PATH = "/" + PREFIX + "/";

    private String path;
    private Format format = Format.NATIVE;

    /**
     * @return The path where the configuration is stored
     */
    public Optional<String> getPath() {
        return Optional.ofNullable(path);
    }

    public void setPath(String path) {
        this.path = path;
    }

    /**
      * @return The configuration format
     */
    public Format getFormat() {
        return format;
    }

    public void setFormat(Format format) {
        if(format != null) {
            this.format = format;
        }
    }

    /**
     * The format the configuration is stored in
     */
    public enum Format {
        /**
         * Stored in YAML format
         */
        YAML,
        /**
         * Stored in JSON format
         */
        JSON,
        /**
         * Stored in Java properties file format
         */
        PROPERTIES,
        /**
         * Stored in the native format provided by the configuration server
         */
        NATIVE,
        /**
         * Each value in the configuration server represents the name of a file and the contents of the file.
         * Useful when using solutions such as git2consul
         */
        FILE
    }
}
