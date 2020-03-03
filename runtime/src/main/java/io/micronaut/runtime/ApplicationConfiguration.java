/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.runtime;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.discovery.ServiceInstance;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Common application configuration.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties(ApplicationConfiguration.PREFIX)
@Primary
@BootstrapContextCompatible
public class ApplicationConfiguration {

    /**
     * Prefix for Micronaut application settings.
     */
    public static final String PREFIX = "micronaut.application";

    /**
     * Property name for Micronaut default charset.
     */
    public static final String DEFAULT_CHARSET = PREFIX + ".default-charset";

    /**
     * Property name for Micronaut application name.
     */
    public static final String APPLICATION_NAME = PREFIX + ".name";

    private Charset defaultCharset = StandardCharsets.UTF_8;
    private String name;
    private InstanceConfiguration instance = new InstanceConfiguration();

    /**
     * @return The default charset to use.
     */
    @SuppressWarnings("unchecked")
    public Charset getDefaultCharset() {
        return defaultCharset;
    }

    /**
     * Default value (UTF-8).
     * @param defaultCharset Set the default charset to use.
     */
    public void setDefaultCharset(Charset defaultCharset) {
        this.defaultCharset = defaultCharset;
    }

    /**
     * The application name. Used to identify the application for purposes of reporting, tracing, service discovery etc.
     * Should be unique.
     *
     * @return The application name
     */
    public Optional<String> getName() {
        return Optional.ofNullable(name);
    }

    /**
     * @param name Set the application name
     */
    public void setName(String name) {
        if (name != null) {
            this.name = NameUtils.hyphenate(name);
        }
    }

    /**
     * @return Configuration for the application instance
     */
    public InstanceConfiguration getInstance() {
        return instance;
    }

    /**
     * @param instance The instance configuration
     */
    public void setInstance(InstanceConfiguration instance) {
        if (instance != null) {
            this.instance = instance;
        }
    }

    /**
     * Configuration for instance settings.
     */
    @ConfigurationProperties(InstanceConfiguration.PREFIX)
    @BootstrapContextCompatible
    public static class InstanceConfiguration {
        /**
         * Prefix for Micronaut instance settings.
         */
        public static final String PREFIX = "instance";

        /**
         * Property name for Micronaut instance id.
         */
        public static final String INSTANCE_ID = ApplicationConfiguration.PREFIX + '.' + PREFIX + ".id";

        private String id;
        private String group;
        private String zone;
        private Map<String, String> metadata = Collections.emptyMap();

        /**
         * @return An optional instance identifier
         */
        public Optional<String> getId() {
            return Optional.ofNullable(id);
        }

        /**
         * @param id The instance identifier
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * @return Any metadata to associate with the instance
         */
        public Map<String, String> getMetadata() {
            return metadata;
        }

        /**
         * @param metadata The metadata to associate with the instance
         */
        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }

        /**
         * @return The instance auto scaling group
         */
        public Optional<String> getGroup() {
            return Optional.ofNullable(group);
        }

        /**
         * @param group The instance auto scaling group
         */
        public void setGroup(String group) {
            this.group = group;
        }

        /**
         * @return The instance availability zone. For example it's possible to configure Nexflix Ribbon to load balance between servers only in a particular zone
         */
        public Optional<String> getZone() {
            if (zone != null) {
                return Optional.of(zone);
            }
            return Optional.ofNullable(getMetadata().get(ServiceInstance.ZONE));
        }

        /**
         * @param zone The instance availability zone
         */
        public void setZone(String zone) {
            this.zone = zone;
        }
    }
}
