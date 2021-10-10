package io.micronaut.inject.configproperties;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;

import java.util.Map;

@ConfigurationProperties("jpa")
public class MyHibernateConfig2 {
    private Map<String, String> properties;

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(
            @MapFormat(transformation = MapFormat.MapTransformation.FLAT) Map<String, String> properties) {
        this.properties = properties;
    }
}
