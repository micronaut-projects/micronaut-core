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
package io.micronaut.context.env;

import io.micronaut.core.cli.CommandLine;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link io.micronaut.context.env.PropertySource} for properties parsed from the command line.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class CommandLinePropertySource extends MapPropertySource {

    /**
     * The position of the loader.
     */
    public static final int POSITION = SystemPropertiesPropertySource.POSITION + 100;

    /**
     * The name of the property source.
     */
    public static final String NAME = "cli";

    /**
     * Construct the CommandLinePropertySource from properties passed from command line.
     *
     * @param commandLine Represents the parsed command line options.
     */
    public CommandLinePropertySource(CommandLine commandLine) {
        super(NAME, resolveValues(commandLine));
    }

    @Override
    public int getOrder() {
        return POSITION;
    }

    private static Map<String, Object> resolveValues(CommandLine commandLine) {
        if (commandLine == null) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, Object> map = new LinkedHashMap<>(commandLine.getUndeclaredOptions());
        for (Map.Entry<Object, Object> entry : commandLine.getSystemProperties().entrySet()) {
            map.put(entry.getKey().toString(), entry.getValue());
        }
        return map;
    }
}

