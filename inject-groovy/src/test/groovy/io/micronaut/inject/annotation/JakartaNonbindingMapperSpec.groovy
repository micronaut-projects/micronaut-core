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
package io.micronaut.inject.annotation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue

class JakartaNonbindingMapperSpec extends AbstractBeanDefinitionSpec {

    void 'a Jakarta nonbinding member is ignored during interceptor selection'() {
        given:
        def context = buildContext('''
package jakartanonbinding

import io.micronaut.aop.*
import jakarta.enterprise.util.Nonbinding
import jakarta.inject.Singleton
import java.lang.annotation.*

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.METHOD])
@Around
@InterceptorBinding(bindMembers = true)
@interface Bound {
    String value()
    @Nonbinding String description() default ""
}

@Singleton
@Bound(value = "a", description = "interceptor")
class Advice implements MethodInterceptor<Object, Object> {
    int calls

    Object intercept(MethodInvocationContext<Object, Object> context) {
        calls++
        return context.proceed()
    }
}

@Singleton
@Bound(value = "a", description = "target")
class Subject {
    String matching() { "done" }

    @Bound(value = "b", description = "target")
    String different() { "done" }
}
''')
        def advice = getBean(context, 'jakartanonbinding.Advice')
        def subject = getBean(context, 'jakartanonbinding.Subject')

        when:
        subject.matching()
        subject.different()

        then:
        advice.calls == 1
        recorded(context.getBeanDefinition(advice.getClass()).annotationMetadata.getAnnotation('jakartanonbinding.Bound')) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE]
        recorded(context.getBeanDefinition(context.classLoader.loadClass('jakartanonbinding.Subject')).annotationMetadata.getAnnotation('jakartanonbinding.Bound')) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE]

        cleanup:
        context.close()
    }

    void 'a Jakarta nonbinding qualifier member is recorded on the qualifier'() {
        given:
        def definition = buildBeanDefinition('jakartanonbindingqualifier.Test', '''
package jakartanonbindingqualifier

import jakarta.enterprise.util.Nonbinding
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import java.lang.annotation.Retention
import static java.lang.annotation.RetentionPolicy.RUNTIME

@Cylinders(8)
@Singleton
class Test {
}

@Qualifier
@Retention(RUNTIME)
@interface Cylinders {
    int value()

    @Nonbinding
    String description() default ""
}
''')
        def metadata = definition.annotationMetadata

        expect:
        recorded(metadata.getAnnotation('jakartanonbindingqualifier.Cylinders')) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE]
        recorded(metadata.getAnnotation(AnnotationUtil.QUALIFIER)) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE]
    }

    private static List<String> recorded(AnnotationValue<?> annotation) {
        annotation.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE) as List
    }
}
