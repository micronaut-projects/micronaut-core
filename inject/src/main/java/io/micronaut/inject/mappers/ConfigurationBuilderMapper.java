package io.micronaut.inject.mappers;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.BeanProperties;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.List;

public class ConfigurationBuilderMapper implements TypedAnnotationMapper<ConfigurationBuilder> {

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<ConfigurationBuilder> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
            AnnotationValue.builder(BeanProperties.class)
                // Configuration properties also includes fields
                .member(BeanProperties.ACCESS_KIND, new BeanProperties.AccessKind[]{BeanProperties.AccessKind.METHOD})
                .member(BeanProperties.VISIBILITY, BeanProperties.Visibility.DEFAULT)
                .member(BeanProperties.INCLUDES, annotation.stringValues(BeanProperties.INCLUDES))
                .member(BeanProperties.EXCLUDES, annotation.stringValues(BeanProperties.EXCLUDES))
                .member(BeanProperties.ALLOW_WRITE_WITH_ZERO_ARGS, annotation.booleanValue("allowZeroArgs").orElse(false))
                .member(BeanProperties.ALLOW_WRITE_WITH_MULTIPLE_ARGS, true)
                .build()
        );
    }

    @Override
    public Class<ConfigurationBuilder> annotationType() {
        return ConfigurationBuilder.class;
    }
}
