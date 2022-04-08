package io.micronaut.inject.annotation;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;

import java.util.Map;

@Requires(property = "spec.name", value = "AnnotatedFieldWithSetterSpec")
@ConfigurationProperties("conf")
public class AnnotatedFieldWithSetter {

    @MapFormat(keyFormat = StringConvention.RAW)
    private Map<String, String> animals;

    public Map<String, String> getAnimals() {
        return animals;
    }

    public void setAnimals(Map<String, String> animals) {
        this.animals = animals;
    }

}
