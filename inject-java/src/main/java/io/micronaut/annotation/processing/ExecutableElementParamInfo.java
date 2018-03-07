/*
 * Copyright 2017 original authors
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
package io.micronaut.annotation.processing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds parameter information for a {@link javax.lang.model.element.ExecutableElement}
 */
class ExecutableElementParamInfo {
    Map<String, Object> parameters = new LinkedHashMap<>();
    Map<String, Object> qualifierTypes = new LinkedHashMap<>();
    Map<String, Map<String, Object>> genericTypes = new LinkedHashMap<>();

    void addParameter(String paramName, Object type) {
        parameters.put(paramName, type);
    }

    void addQualifierType(String paramName, Object qualifier) {
        qualifierTypes.put(paramName, qualifier);
    }

    void addGenericTypes(String paramName, Map<String, Object> generics) {
        genericTypes.put(paramName, generics);
    }

    Map<String, Object> getParameters() {
        return Collections.unmodifiableMap(parameters);
    }

    Map<String, Object> getQualifierTypes() {
        return Collections.unmodifiableMap(qualifierTypes);
    }

    Map<String, Map<String, Object>> getGenericTypes() {
        return Collections.unmodifiableMap(genericTypes);
    }
}
