package io.micronaut.inject.mappers;

import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.BeanProperties;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.List;

public class ConfigurationPropertiesMapper implements TypedAnnotationMapper<ConfigurationReader> {

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<ConfigurationReader> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
            AnnotationValue.builder(BeanProperties.class)
                // Configuration properties also includes fields
                .member(BeanProperties.ACCESS_KIND, new BeanProperties.AccessKind[]{BeanProperties.AccessKind.FIELD, BeanProperties.AccessKind.METHOD})
                .member(BeanProperties.VISIBILITY, BeanProperties.Visibility.DEFAULT)
                .member(BeanProperties.INCLUDES, annotation.stringValues(BeanProperties.INCLUDES))
                .member(BeanProperties.EXCLUDES, annotation.stringValues(BeanProperties.EXCLUDES))
                .build()
        );
    }

    @Override
    public Class<ConfigurationReader> annotationType() {
        return ConfigurationReader.class;
    }
}
