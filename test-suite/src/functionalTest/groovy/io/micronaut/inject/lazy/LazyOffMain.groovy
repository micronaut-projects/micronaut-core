package io.micronaut.inject.lazy

import groovy.transform.CompileStatic
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers

/**
 * Starts the application of {@link LazyBeanDefinitionsSpec}, which is on the class path, with the lazy bean
 * definitions switched off, and prints what a few lookups answer.
 */
@CompileStatic
class LazyOffMain {

    static void main(String[] args) {
        ClassLoader loader = LazyOffMain.classLoader
        ApplicationContext context = ApplicationContext.builder()
            .classLoader(loader)
            .deduceEnvironment(false)
            .environments('lazytest')
            .start()
        try {
            String v8 = context.getBean(loader.loadClass('demo.V8')).getClass().name
            List<String> handlers = context.getBeanDefinitions(Qualifiers.byStereotype((Class) loader.loadClass('demo.Handler')))
                .collect { it.beanType.name }
                .sort()
            println "ANSWER v8=$v8 handlers=$handlers"
        } finally {
            context.close()
        }
    }
}
