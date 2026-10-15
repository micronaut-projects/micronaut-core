package io.micronaut.inject.records

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement

class CompiledRecordPropertiesSpec extends AbstractTypeElementSpec {

    // https://github.com/micronaut-projects/micronaut-core/issues/13404
    void "a compiled record with covariant interface accessors keeps the record component types"() {
        when:
        def properties = buildClassElement('''
package test;

class Test {
}
''') { ClassElement classElement ->
            ClassElement record = classElement.visitorContext.getClassElement(CovariantRecord.name).get()
            record.getBeanProperties().collectEntries {
                [(it.name): [it.type.name, it.readMethod.get().returnType.name]]
            }
        }

        then:
        properties == [
                id     : [String.name, String.name],
                details: [CovariantRecord.Details.name, CovariantRecord.Details.name]
        ]
    }

    void "a source record with covariant interface accessors keeps the record component types"() {
        when:
        def properties = buildClassElement('''
package test;

record Test(String id, Test.Details details) implements Identified<String>, HasDetails {
    interface View {
    }
    record Details(String value) implements View {
    }
}

interface Identified<I> {
    I id();
}

interface HasDetails {
    Test.View details();
}
''') { ClassElement classElement ->
            classElement.getBeanProperties().collectEntries {
                [(it.name): [it.type.name, it.readMethod.get().returnType.name]]
            }
        }

        then:
        properties == [
                id     : [String.name, String.name],
                details: ['test.Test$Details', 'test.Test$Details']
        ]
    }
}
