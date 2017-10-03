package org.particleframework.annotation.processing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
