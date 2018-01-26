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
package org.particleframework.runtime;

import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.context.annotation.Primary;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Common application configuration
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties("particle.application")
@Primary
public class ApplicationConfiguration {

    public static final String DEFAULT_CHARSET = "particle.defaultCharset";

    private Charset defaultCharset = StandardCharsets.UTF_8;
    private String name;
    @SuppressWarnings("unchecked")
    private Map<String, String> info = Collections.EMPTY_MAP;

    /**
     * @return The default charset to use
     */
    public Charset getDefaultCharset() {
        return defaultCharset;
    }

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

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Arbitrary application information
     * @return The application information
     */
    public Map<String, String> getInfo() {
        return info;
    }

    public void setInfo(Map<String, String> info) {
        this.info = info;
    }
}
