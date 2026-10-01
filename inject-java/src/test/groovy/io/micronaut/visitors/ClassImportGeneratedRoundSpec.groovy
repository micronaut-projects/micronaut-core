package io.micronaut.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * A generated source annotated only {@code @ClassImport} arrives in a later compilation
 * round and must still trigger the bean definition processor.
 */
class ClassImportGeneratedRoundSpec extends AbstractTypeElementSpec {

    void "test a generated class annotated only with @ClassImport produces bean definitions for the imported classes"() {
        given:
            ApplicationContext context = buildContext('test.TriggerBean', '''
package test;

import io.micronaut.visitors.GenerateImporter;

@GenerateImporter
class TriggerBean {}
''')

        when:"the imported bean is resolved"
            def bean = getBean(context, 'io.micronaut.visitors.MyImportedBean')

        then:"the generated import triggered processing in its round"
            bean != null

        cleanup:
            context?.close()
    }
}
