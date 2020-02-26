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
package io.micronaut.context.env;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Loads properties from environment variables via {@link System#getenv()}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class EnvironmentPropertySource extends MapPropertySource {

    /**
     * The position of the loader.
     */
    public static final int POSITION = SystemPropertiesPropertySource.POSITION - 100;

    /**
     * Constant for Environment property source.
     */
    public static final String NAME = "env";

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
    public PropertyConvention getConvention() {
        return PropertyConvention.ENVIRONMENT_VARIABLE;
    }

    private static Map getEnv(@Nullable List<String> includes, @Nullable List<String> excludes) {
        Map<String, String> env;
        if (includes != null || excludes != null) {
            env = new HashMap<>(System.getenv());
            Iterator<String> it = env.keySet().iterator();
            while (it.hasNext()) {
                String envVar = it.next();
                if (excludes != null && excludes.contains(envVar)) {
                    it.remove();
                }
                if (includes != null && !includes.contains(envVar)) {
                    it.remove();
                }
            }
        } else {
            env = System.getenv();
        }
        return env;
    }
}
