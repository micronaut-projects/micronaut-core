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
package io.micronaut.kotlin.processing.inject.ast

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery

class KotlinParameterElementDocumentationSpec extends AbstractKotlinCompilerSpec {

    void "class-level KDoc documents constructor parameters, not regular method parameters"() {
        expect:
        buildClassElement('test.Test', '''
package test

/**
 * Test class.
 *
 * @property percentage the class-level percentage doc
 */
class Test(val percentage: Int) {

    fun updatePercentage(percentage: Int) {
    }
}
''') { ClassElement ce ->
            def constructorParam = ce.primaryConstructor.get().parameters[0]
            def methodParam = ce.getEnclosedElements(ElementQuery.ALL_METHODS)
                .find { it.name == 'updatePercentage' }.parameters[0]

            // The class @property tag documents the constructor parameter of the same name...
            assert constructorParam.getDocumentation(true).get() == 'the class-level percentage doc'
            // ...but must not bleed onto an unrelated method parameter that happens to share the name.
            assert !methodParam.getDocumentation(true).isPresent()
        }
    }
}
