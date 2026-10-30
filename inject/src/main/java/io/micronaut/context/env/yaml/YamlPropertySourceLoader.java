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
package io.micronaut.context.env.yaml;

import io.micronaut.context.env.AbstractPropertySourceLoader;
import io.micronaut.context.env.CachedEnvironment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads properties from a YML file.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class YamlPropertySourceLoader extends AbstractPropertySourceLoader {

    /**
     * System property that sets the maximum number of aliases (anchor references) to collections
     * allowed in a single YAML document. SnakeYAML's default is {@value #DEFAULT_MAX_ALIASES_FOR_COLLECTIONS}.
     *
     * <p>The limit is the only guard against "billion laughs" style documents that exhaust memory and CPU.
     * Nested maps are flattened into property keys, so every alias to a map is expanded, and a document
     * that stays within a raised limit can still expand exponentially (for example 20 levels of 10 aliases
     * produce 10^20 keys with a limit of 200). Only raise it when all YAML configuration sources are trusted.</p>
     *
     * <p>YAML files are parsed while the environment is built, so this can only be set as a
     * system property or through {@link #MAX_ALIASES_FOR_COLLECTIONS_ENV}, not in a configuration file.</p>
     *
     * @since 5.3.0
     */
    public static final String MAX_ALIASES_FOR_COLLECTIONS_PROPERTY = "micronaut.config.yaml.max-aliases-for-collections";

    /**
     * Environment variable alternative to {@link #MAX_ALIASES_FOR_COLLECTIONS_PROPERTY}.
     * The system property takes precedence.
     *
     * @since 5.3.0
     */
    public static final String MAX_ALIASES_FOR_COLLECTIONS_ENV = "MICRONAUT_CONFIG_YAML_MAX_ALIASES_FOR_COLLECTIONS";

    /**
     * SnakeYAML's default maximum number of aliases for collections.
     *
     * @since 5.3.0
     */
    public static final int DEFAULT_MAX_ALIASES_FOR_COLLECTIONS = 50;

    private final AtomicBoolean relaxedAliasLimitWarned = new AtomicBoolean();

    public YamlPropertySourceLoader() {
    }

    public YamlPropertySourceLoader(boolean logEnabled) {
        super(logEnabled);
    }

    @Override
    public boolean isEnabled() {
        return isSnakeYamlPresent();
    }

    @Override
    public Set<String> getExtensions() {
        return CollectionUtils.setOf("yml", "yaml");
    }

    @Override
    protected void processInput(String name, InputStream input, Map<String, Object> finalMap) {
        // workaround for Graal which returns null
        if (System.getProperty("java.runtime.name") == null) {
            System.setProperty("java.runtime.name", "Unknown");
        }

        Iterable<Object> objects = Wrapper.loadObjects(input, resolveMaxAliasesForCollections());
        Iterator<Object> i = objects.iterator();
        if (i.hasNext()) {
            while (i.hasNext()) {
                Object object = i.next();
                if (object instanceof Map map) {
                    log.trace("Processing YAML: {}", map);
                    String prefix = "";
                    processMap(finalMap, map, prefix);
                }
            }
        } else {
            log.trace("PropertySource [{}] produced no YAML content", name);
        }
    }

    private int resolveMaxAliasesForCollections() {
        String source = MAX_ALIASES_FOR_COLLECTIONS_PROPERTY;
        String value = CachedEnvironment.getProperty(MAX_ALIASES_FOR_COLLECTIONS_PROPERTY);
        if (StringUtils.isEmpty(value)) {
            source = MAX_ALIASES_FOR_COLLECTIONS_ENV;
            value = CachedEnvironment.getenv(MAX_ALIASES_FOR_COLLECTIONS_ENV);
        }
        if (StringUtils.isEmpty(value)) {
            return DEFAULT_MAX_ALIASES_FOR_COLLECTIONS;
        }
        int maxAliases;
        try {
            maxAliases = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigurationException(invalidMaxAliasesMessage(value, source), e);
        }
        if (maxAliases < 1) {
            throw new ConfigurationException(invalidMaxAliasesMessage(value, source));
        }
        if (maxAliases > DEFAULT_MAX_ALIASES_FOR_COLLECTIONS && relaxedAliasLimitWarned.compareAndSet(false, true)) {
            log.warn("YAML alias limit for collections raised from {} to {} via [{}]. Aliased maps can now expand exponentially into configuration keys; only do this for trusted configuration sources.",
                DEFAULT_MAX_ALIASES_FOR_COLLECTIONS, maxAliases, source);
        }
        return maxAliases;
    }

    private static String invalidMaxAliasesMessage(String value, String source) {
        return "Invalid value [" + value + "] for [" + source + "]: must be an integer between 1 and " + Integer.MAX_VALUE;
    }

    private static boolean isSnakeYamlPresent() {
        try {
            Class<Yaml> yamlClass = Yaml.class;
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static final class Wrapper {
        // in nested class to prevent NCDFE

        private static Iterable<Object> loadObjects(InputStream input, int maxAliasesForCollections) {
            LoaderOptions loaderOptions = new LoaderOptions();
            loaderOptions.setMaxAliasesForCollections(maxAliasesForCollections);
            Yaml yaml = new Yaml(new CustomSafeConstructor(loaderOptions));
            return yaml.loadAll(input);
        }
    }
}
