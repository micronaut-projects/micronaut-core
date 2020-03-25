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
package io.micronaut.discovery.client;

import java.util.*;

/**
 *  Helpers to reduce redundant code between different Client implementations.
 */
public class ClientUtil {

    /**
     * Calculates property source names. This is used across several clients to make naming consistent.
     * @param prefix fullName or prefix to the environment, or application specific configuration
     * @param activeNames active environments which configurations can be created for
     * @param separator The separator string
     * @return Set of names to be used for each PropertySource
     */
    public static Set<String> calcPropertySourceNames(String prefix, Collection<String> activeNames, String separator) {
        Set<String> propertySourceNames;
        if (prefix.contains(separator)) {

            String[] tokens = prefix.split(separator);
            if (tokens.length == 1) {
                propertySourceNames = Collections.singleton(tokens[0]);
            } else {
                String name = tokens[0];
                Set<String> newSet = new HashSet<>(tokens.length - 1);
                for (int j = 1; j < tokens.length; j++) {
                    String envName = tokens[j];
                    if (!activeNames.contains(envName)) {
                        return Collections.emptySet();
                    }
                    newSet.add(name + '[' + envName + ']');
                }
                propertySourceNames = newSet;
            }
        } else {
            propertySourceNames = Collections.singleton(prefix);
        }
        return propertySourceNames;
    }

    /**
     * Resolves the environment from a property source name created
     * by {@link #calcPropertySourceNames(String, Collection, String)}.
     *
     * @param fileName The property source name
     * @param activeNames The active environments
     * @return The environment name
     */
    public static String resolveEnvironment(String fileName, Collection<String> activeNames) {
        if (fileName.endsWith("]")) {
            int envIdx = fileName.indexOf('[') + 1;
            String envName = fileName.substring(envIdx, fileName.length() - 1);
            if (activeNames.contains(envName)) {
                return envName;
            }
        }
        return null;
    }

}
