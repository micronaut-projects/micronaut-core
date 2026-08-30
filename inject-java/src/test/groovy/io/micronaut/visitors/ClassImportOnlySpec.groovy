package io.micronaut.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * {@code @ClassImport} is processed for the classes it names rather than for a stereotype
 * it carries, so a class annotated with nothing else must still trigger the bean definition
 * processor.
 */
class ClassImportOnlySpec extends AbstractTypeElementSpec {

    void "test a class annotated only with @ClassImport produces bean definitions for the imported classes"() {
        given:
            ApplicationContext context = buildContext('test.Importer', '''
package test;

import io.micronaut.context.annotation.ClassImport;
import jakarta.inject.Singleton;

@ClassImport(classes = io.micronaut.visitors.MyImportedBean.class, annotate = Singleton.class)
class Importer {}
''')

        when:"the imported bean is resolved"
            def bean = getBean(context, 'io.micronaut.visitors.MyImportedBean')

        then:"the import alone triggered processing and produced a bean definition"
            bean != null
            bean.greet() == 'Hello'

        cleanup:
            context?.close()
    }
}
