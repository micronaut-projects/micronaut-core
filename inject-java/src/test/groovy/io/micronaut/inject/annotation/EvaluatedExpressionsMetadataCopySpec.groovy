/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.BeanDefinition

class EvaluatedExpressionsMetadataCopySpec extends AbstractTypeElementSpec {

    void "test hasEvaluatedExpressions() is retained by clone() and getDeclaredMetadata()"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Expr', '''
package test;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Singleton;

import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Singleton
@CustomAnnotation("#{ 1 + 1 }")
class Expr {
}

@Retention(RUNTIME)
@interface CustomAnnotation {
    String value();
}
''')

        AnnotationMetadata annotationMetadata = definition.getAnnotationMetadata().getTargetAnnotationMetadata()

        expect:
        annotationMetadata instanceof DefaultAnnotationMetadata
        annotationMetadata.hasEvaluatedExpressions()
        ((DefaultAnnotationMetadata) annotationMetadata).clone().hasEvaluatedExpressions()
        annotationMetadata.getDeclaredMetadata().hasEvaluatedExpressions()
    }

    void "test hasEvaluatedExpressions() is false for metadata without expressions"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.NoExpr', '''
package test;

import jakarta.inject.Singleton;

import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Singleton
@CustomAnnotation("plain")
class NoExpr {
}

@Retention(RUNTIME)
@interface CustomAnnotation {
    String value();
}
''')

        AnnotationMetadata annotationMetadata = definition.getAnnotationMetadata().getTargetAnnotationMetadata()

        expect:
        annotationMetadata instanceof DefaultAnnotationMetadata
        !annotationMetadata.hasEvaluatedExpressions()
        !((DefaultAnnotationMetadata) annotationMetadata).clone().hasEvaluatedExpressions()
        !annotationMetadata.getDeclaredMetadata().hasEvaluatedExpressions()
    }
}
