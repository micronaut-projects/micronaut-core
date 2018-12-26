package io.micronaut.inject.context.processor;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.processor.BeanDefinitionProcessor;
import io.micronaut.inject.BeanDefinition;

import java.util.HashSet;
import java.util.Set;

@Context
public class ProcessedAnnotationProcessor implements BeanDefinitionProcessor<ProcessedAnnotation> {
    private Set<BeanDefinition<?>> beans = new HashSet<>();

    public ProcessedAnnotationProcessor() {
        System.out.println("Starting");
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, BeanContext object) {
        beans.add(beanDefinition);
    }

    public Set<BeanDefinition<?>> getBeans() {
        return beans;
    }
}
