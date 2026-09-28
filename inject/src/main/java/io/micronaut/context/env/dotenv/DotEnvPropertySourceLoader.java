/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.context.env.dotenv;

import io.micronaut.context.env.AbstractPropertySourceLoader;
import io.micronaut.context.env.ActiveEnvironment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads properties from .env files.
 * Supports variable substitution, escape sequences, and multiple quote types.
 *
 * @author Your Name
 * @since 4.x
 */
public class DotEnvPropertySourceLoader extends AbstractPropertySourceLoader {

    /**
     * File extension for .env files.
     */
    public static final String FILE_EXTENSION = "env";
    private final DotEnvParser parser;
    private final DotEnvVariableResolver resolver;

    public DotEnvPropertySourceLoader() {
        parser = new DotEnvParser();
        resolver = new DotEnvVariableResolver();
    }

    public DotEnvPropertySourceLoader(boolean logEnabled) {
        super(logEnabled);
        parser = new DotEnvParser(log);
        resolver = new DotEnvVariableResolver(log);
    }

    @Override
    public Set<String> getExtensions() { return Collections.singleton(FILE_EXTENSION); }

    @Override
    public Optional<PropertySource> loadEnv(String resourceName, ResourceLoader resourceLoader, ActiveEnvironment activeEnvironment) {
        int order = getOrder() + 1 + activeEnvironment.getPriority();
        if (isEnabled()) {
            Set<String> extensions = getExtensions();
            for (String ext : extensions) {
                String fileName = resourceName + "." + ext + "." + activeEnvironment.getName();
                Map<String, Object> finalMap = loadProperties(resourceLoader, fileName, fileName);

                if (!finalMap.isEmpty()) {
                    return Optional.of(
                        createPropertySource(fileName, finalMap, order, PropertySource.Origin.of(fileName))
                    );
                }
            }
        }

        return Optional.empty();
    }

    @Override
    protected void processInput(String name, InputStream input, Map<String, Object> finalMap) throws IOException {
        DotEnvParseContext context = parser.parse(input);
        resolver.resolveVariables(context);
        finalMap.putAll(context.getResolvedVars());
    }
}
