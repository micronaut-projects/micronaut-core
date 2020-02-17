package io.micronaut.inject.configproperties;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties("map")
public class MapProperties {

    Map<String, Object> field;

    private Map<String, Object> setter;

    public Map<String, Object> getSetter() {
        return setter;
    }

    public void setSetter(Map<String, Object> setter) {
        this.setter = setter;
    }
}
