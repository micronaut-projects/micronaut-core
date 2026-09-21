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
package io.micronaut.ast.groovy.annotation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * A Groovy annotation member whose default references a constant declared in scope, which Groovy represents as a
 * {@code VariableExpression}, reports that default like any other.
 */
class ConstantReferenceDefaultsSpec extends AbstractBeanDefinitionSpec {

    void 'test a default referencing a constant in scope is reported'() {
        given:
        ConstantDefaultsRecordingVisitor.reset()

        when:
        buildBeanDefinition('constdefaults.ConstantUser', '''
package constdefaults

import io.micronaut.context.annotation.Bean
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Retention(RetentionPolicy.RUNTIME)
@interface WithConstant {
    String DEFAULT_NAME = "from-constant"

    String name() default DEFAULT_NAME
}

@WithConstant
@Bean
class ConstantUser {
}
''')

        then:
        ConstantDefaultsRecordingVisitor.fromContext?.get('name') == 'from-constant'
        ConstantDefaultsRecordingVisitor.fromAnnotation?.get('name') == 'from-constant'
    }

    static class ConstantDefaultsRecordingVisitor implements TypeElementVisitor<Object, Object> {

        static Map<CharSequence, Object> fromContext = null
        static Map<CharSequence, Object> fromAnnotation = null

        static void reset() {
            fromContext = null
            fromAnnotation = null
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.simpleName == "ConstantUser") {
                fromContext = context.getAnnotationDefaultValues("constdefaults.WithConstant")
                fromAnnotation = element.getAnnotation("constdefaults.WithConstant")?.getDefaultValues()
            }
        }
    }
}
