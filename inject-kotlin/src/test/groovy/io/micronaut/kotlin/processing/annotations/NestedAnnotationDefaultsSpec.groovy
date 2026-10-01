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
package io.micronaut.kotlin.processing.annotations

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * The defaults of a nested annotation class resolve under KSP, whether the annotation is named by its binary name
 * ({@code Holder$Inner}, the form annotation names take elsewhere) or by its source name ({@code Holder.Inner}).
 */
class NestedAnnotationDefaultsSpec extends AbstractKotlinCompilerSpec {

    void 'test nested annotation defaults are reported through the visitor context'() {
        given:
        NestedDefaultsRecordingVisitor.reset()

        when:
        buildBeanDefinition('nesteddefaults.NestedUser', '''
package nesteddefaults

import io.micronaut.context.annotation.Bean

class Holder {
    annotation class Inner(val name: String = "inner", val count: Int = 3)
}

@Holder.Inner
@Bean
class NestedUser
''')

        then:
        NestedDefaultsRecordingVisitor.byBinaryName.collectEntries { k, v -> [(k.toString()): v] } == [name: 'inner', count: 3]
        NestedDefaultsRecordingVisitor.bySourceName.collectEntries { k, v -> [(k.toString()): v] } == [name: 'inner', count: 3]
    }

    static class NestedDefaultsRecordingVisitor implements TypeElementVisitor<Object, Object> {

        static Map<CharSequence, Object> byBinaryName = null
        static Map<CharSequence, Object> bySourceName = null

        static void reset() {
            byBinaryName = null
            bySourceName = null
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.simpleName == "NestedUser") {
                byBinaryName = context.getAnnotationDefaultValues('nesteddefaults.Holder$Inner')
                bySourceName = context.getAnnotationDefaultValues('nesteddefaults.Holder.Inner')
            }
        }
    }
}
