package io.micronaut.inject.beanrefdef;

import io.micronaut.context.AbstractInitializableBeanDefinition;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.ExecutableMethodsDefinition;

import java.util.Map;

public class FooBarBeanDefinition extends AbstractInitializableBeanDefinition<FooBar> {

    protected FooBarBeanDefinition(Class<FooBar> beanType,
                                   MethodOrFieldReference constructor,
                                   AnnotationMetadata annotationMetadata,
                                   @Nullable MethodReference[] methodInjection,
                                   @Nullable FieldReference[] fieldInjection,
                                   @Nullable AnnotationReference[] annotationInjection,
                                   ExecutableMethodsDefinition<FooBar> executableMethodsDefinition,
                                   Map<String, Argument<?>[]> typeArgumentsMap,
                                   PrecalculatedInfo precalculatedInfo) {
        super(beanType, constructor, annotationMetadata, methodInjection, fieldInjection, annotationInjection, executableMethodsDefinition, typeArgumentsMap, precalculatedInfo);
    }

//    @Override
//    public String getBeanDefinitionName() {
//        return null;
//    }
//
//    @Override
//    public BeanDefinition<FooBar> load() {
//        return null;
//    }
//
//    @Override
//    public boolean isPresent() {
//        return false;
//    }

    @Override
    public FooBar instantiate(BeanResolutionContext resolutionContext, BeanContext context) throws BeanInstantiationException {
        return null;
    }
}
