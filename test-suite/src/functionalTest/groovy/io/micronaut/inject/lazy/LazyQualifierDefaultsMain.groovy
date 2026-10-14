package io.micronaut.inject.lazy

import groovy.transform.CompileStatic
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers

/**
 * Starts the application of {@link LazyBeanDefinitionsSpec}, which is on the class path, with the lazy bean
 * definitions and the switches the arguments give, in a JVM of its own: the defaults of annotations are registered
 * JVM-wide, so the first lookup of a qualifier by its values is only the first in a fresh JVM. Prints what it answers.
 */
@CompileStatic
class LazyQualifierDefaultsMain {

    static void main(String[] args) {
        ApplicationContext context = ApplicationContext.builder()
            .classLoader(LazyQualifierDefaultsMain.classLoader)
            .deduceEnvironment(false)
            .environments('lazytest')
            .start()
        try {
            println "ANSWER large=${lookup(context, 'large')} small=${lookup(context, 'small')}"
        } finally {
            context.close()
        }
    }

    @SuppressWarnings(['rawtypes', 'unchecked'])
    private static List<String> lookup(ApplicationContext context, String size) {
        AnnotationValue value = AnnotationValue.builder('demo.Flavor').value('vanilla').member('size', size).build()
        List<String> names = []
        for (BeanDefinition<Object> definition : context.getBeanDefinitions(Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, value))) {
            if (definition.beanType.name.startsWith('demo.')) {
                names.add(definition.beanType.name)
            }
        }
        return names.sort()
    }
}
