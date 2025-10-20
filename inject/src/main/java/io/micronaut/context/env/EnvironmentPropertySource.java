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

import io.micronaut.core.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loads properties from environment variables via {@link System#getenv()}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class EnvironmentPropertySource extends MapPropertySource {
    /**
     * Converts list elements defined as _0 or _0_ into names that the resolver can understand; i.e., [0].
     */
    private static final Pattern LIST_CONVERTER_REGEX = Pattern.compile("_([0-9]{1,2})(?:_|$)");

    /**
     * The position of the loader.
     */
    public static final int POSITION = SystemPropertiesPropertySource.POSITION - 100;

    /**
     * Constant for Environment property source.
     */
    public static final String NAME = "env";
    public static final Origin ORIGIN = Origin.of("System.env");

    /**
     * Default constructor.
     */
    public EnvironmentPropertySource() {
        super(NAME, getEnv(null, null));
    }

    /**
     * Allows for control over which environment variables are included.
     *
     * @param includes The environment variables to include in configuration
     * @param excludes The environment variables to exclude from configuration
     */
    public EnvironmentPropertySource(@Nullable List<String> includes, @Nullable List<String> excludes) {
        super(NAME, getEnv(includes, excludes));
    }

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Override
    public Origin getOrigin() {
        return ORIGIN;
    }

    @Override
    public PropertyConvention getConvention() {
        return PropertyConvention.ENVIRONMENT_VARIABLE;
    }

    static Map getEnv(@Nullable List<String> includes, @Nullable List<String> excludes) {
        return getEnv(CachedEnvironment.getenv(), includes, excludes);
    }

    static Map getEnv(Map<String, String> env, @Nullable List<String> includes, @Nullable List<String> excludes) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String envVar = entry.getKey();
            if (excludes != null && excludes.contains(envVar)) {
                continue;
            }
            if (includes != null && !includes.contains(envVar)) {
                continue;
            }
            
            String convertedEnvVar = LIST_CONVERTER_REGEX.matcher(envVar).replaceAll("[$1]");
            
            result.put(convertedEnvVar, entry.getValue());
        }
        
        return result;
    }
}
