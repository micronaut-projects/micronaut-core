/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.context.env.groovy;

import io.micronaut.context.env.AbstractPropertySourceLoader;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Loads properties from a Groovy script.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class GroovyPropertySourceLoader extends AbstractPropertySourceLoader {

    @Override
    public int getOrder() {
        return AbstractPropertySourceLoader.DEFAULT_POSITION;
    }

    @Override
    protected void processInput(String name, InputStream input, Map<String, Object> finalMap) throws IOException {
        ConfigurationEvaluator evaluator = new ConfigurationEvaluator();
        try {
            Map<String, Object> configurationValues = evaluator.evaluate(input);
            if (!configurationValues.isEmpty()) {
                finalMap.putAll(configurationValues);
            }
        } catch (Throwable e) {
            throw new ConfigurationException("Exception occurred reading configuration [" + name + "]: " + e.getMessage(), e);
        }

    }

    @Override
    protected Optional<InputStream> readInput(ResourceLoader resourceLoader, String fileName) {
        Stream<URL> urls = resourceLoader.getResources(fileName);
        Stream<URL> urlStream = urls.filter(url -> !url.getPath().contains("src/main/groovy"));
        Optional<URL> config = urlStream.findFirst();
        if (config.isPresent()) {
            return config.flatMap(url -> {
                try {
                    return Optional.of(url.openStream());
                } catch (IOException e) {
                    throw new ConfigurationException("Exception occurred reading configuration [" + fileName + "]: " + e.getMessage(), e);
                }
            });
        }
        return Optional.empty();
    }

    @Override
    public Set<String> getExtensions() {
        return Collections.singleton("groovy");
    }
}
