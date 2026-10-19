/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.kotlin.processing.ksp

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.annotation.processing.test.KotlinCompiler
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.inject.ast.ClassElement
import spock.lang.PendingFeature

/**
 * A {@code @JvmRecord data class} is a Java record (KSP #2813): an introspection of it builds it through its canonical
 * constructor and reads its components through the accessors named after them.
 */
class JvmRecordIntrospectionSpec extends AbstractKotlinCompilerSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.core.annotation.Introspected

@Introspected
@JvmRecord
data class Point(val x: Int, val y: Int, val label: String?, val tags: List<String> = emptyList())
'''

    void setup() {
        KotlinCompiler.setJvmTarget('17')
    }

    void cleanup() {
        KotlinCompiler.setJvmTarget(null)
    }

    void "the record components are read as properties"() {
        when:
        Map<String, Object> view = buildClassElementMapped('test.Point', SOURCE) { ClassElement ce ->
            [
                    accessors : ce.beanProperties.collectEntries { [(it.name): [it.type.name, it.readMethod.map { m -> m.name }.orElse(null), it.isReadOnly()]] },
                    parameters: ce.primaryConstructor.get().parameters*.name
            ]
        }

        then:
        view.accessors == [
                x    : ['int', 'x', true],
                y    : ['int', 'y', true],
                label: ['java.lang.String', 'label', true],
                tags : ['java.util.List', 'tags', true]
        ]
        view.parameters == ['x', 'y', 'label', 'tags']
    }

    // A @JvmRecord class compiles to a java.lang.Record, as a Java processor sees it, but KotlinClassElement does not
    // override isRecord(). Nothing observed depends on it, the introspection reads the components through the
    // accessors anyway, but the processors take the record paths (AstBeanPropertiesUtils,
    // ConfigurationMetadataWriterVisitor) only for a class reporting itself as one.
    @PendingFeature(reason = "KotlinClassElement.isRecord() is false for a @JvmRecord class")
    void "a @JvmRecord class is a record"() {
        expect:
        buildClassElementMapped('test.Point', SOURCE) { ClassElement ce -> ce.isRecord() }
    }

    void "an introspection builds the record and reads its components"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Point', SOURCE)

        when:
        def point = introspection.instantiate(1, 2, null, ['a'])

        then:
        point.class.isRecord()
        introspection.constructorArguments*.name == ['x', 'y', 'label', 'tags']
        introspection.propertyNames as List == ['x', 'y', 'label', 'tags']
        introspection.getRequiredProperty('x', Integer).get(point) == 1
        introspection.getRequiredProperty('y', Integer).get(point) == 2
        introspection.getRequiredProperty('label', String).get(point) == null
        introspection.getRequiredProperty('tags', List).get(point) == ['a']
        introspection.getRequiredProperty('x', Integer).isReadOnly()

        when: 'a component is changed through a copy'
        def moved = introspection.getRequiredProperty('x', Integer).withValue(point, 5)

        then:
        moved == point.copy(5, 2, null, ['a'])
    }
}
