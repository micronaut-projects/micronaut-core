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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wraps imported property sources with canonical identity metadata.
 */
final class ImportedPropertySourceFactory {

    private ImportedPropertySourceFactory() {
    }

    /**
     * Copy an imported property source into a canonical wrapper.
     *
     * @param imported Imported source
     * @param canonicalLocation Canonical location name
     * @param order Source order
     * @param convention Property naming convention
     * @return Wrapped property source
     */
    static PropertySource wrap(PropertySource imported,
                               String canonicalLocation,
                               int order,
                               PropertySource.PropertyConvention convention) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : imported) {
            values.put(key, imported.get(key));
        }
        return new MapPropertySource(canonicalLocation, values) {
            @Override
            public int getOrder() {
                return order;
            }

            @Override
            public PropertyConvention getConvention() {
                return convention;
            }

            @Override
            public Origin getOrigin() {
                return PropertySource.Origin.of(canonicalLocation);
            }
        };
    }
}
