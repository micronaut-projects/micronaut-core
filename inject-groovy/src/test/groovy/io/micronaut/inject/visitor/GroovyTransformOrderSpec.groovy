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
package io.micronaut.inject.visitor

import io.micronaut.ast.groovy.TypeElementVisitorTransform
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import org.codehaus.groovy.control.MultipleCompilationErrorsException

/**
 * Groovy applies the local AST transforms of a phase (records, {@code @TupleConstructor}, {@code @Canonical},
 * {@code @Immutable}) after the global ones, so the type element visitors and the bean definitions must run after them.
 */
class GroovyTransformOrderSpec extends AbstractBeanDefinitionSpec {

    void setup() {
        ConstructorArityVisitor.SEEN.clear()
    }

    void "visitors see the constructors and methods Groovy's own transforms generate"() {
        when:
        buildClassLoader('''
package transformorder

import groovy.transform.*

record Person(Long id, String name) {}

@TupleConstructor
class Tuple { Long id; String name }

@Canonical
class Canon { Long id; String name }

@Immutable
class Imm { Long id; String name }
''')

        then:
        ConstructorArityVisitor.SEEN['transformorder.Person'].startsWith('record=true primary=2')
        ConstructorArityVisitor.SEEN['transformorder.Tuple'].startsWith('record=false primary=2')
        ConstructorArityVisitor.SEEN['transformorder.Canon'].startsWith('record=false primary=2')
        ConstructorArityVisitor.SEEN['transformorder.Canon'].contains('toString')
        ConstructorArityVisitor.SEEN['transformorder.Imm'].startsWith('record=false primary=2')
    }

    void "a record singleton is created through its canonical constructor"() {
        given:
        def context = buildContext('''
package transformorder

import jakarta.inject.Singleton

@Singleton
class Dep {}

@Singleton
record Rec(Dep dep) {}
''')

        expect:
        getBean(context, 'transformorder.Rec').dep() != null

        cleanup:
        context.close()
    }

    void "a tuple constructor singleton is created through the generated constructor"() {
        given:
        def context = buildContext('''
package transformorder

import groovy.transform.TupleConstructor
import jakarta.inject.Singleton

@Singleton
class Dep {}

@Singleton
@TupleConstructor
class Svc { Dep dep }
''')

        expect:
        getBean(context, 'transformorder.Svc').dep != null

        cleanup:
        context.close()
    }

    void "visitors left on the thread by a failed compilation are replaced"() {
        when: 'a compilation fails after the visitors were loaded, before they could be cleared'
        buildClassLoader('''
package transformorder

class Broken {
    Unknown unknown
}
''')

        then:
        thrown(MultipleCompilationErrorsException)
        TypeElementVisitorTransform.loadedVisitors.get() != null
        def starts = ConstructorArityVisitor.starts

        when: 'the next compilation on the thread'
        buildClassLoader('''
package transformorder

class Fine {}
''')

        then: 'starts fresh visitors and clears them when done'
        ConstructorArityVisitor.starts == starts + 1
        ConstructorArityVisitor.SEEN.containsKey('transformorder.Fine')
        TypeElementVisitorTransform.loadedVisitors.get() == null
    }
}
