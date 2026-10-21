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
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue

class JakartaNonbindingMapperSpec extends AbstractKotlinCompilerSpec {

    void 'a Jakarta nonbinding member is ignored during interceptor selection'() {
        given:
        def context = buildContext('''
package jakartanonbinding

import io.micronaut.aop.*
import jakarta.enterprise.util.Nonbinding
import jakarta.inject.Singleton

@Retention
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Around
@InterceptorBinding(bindMembers = true)
annotation class Bound(val value: String, @get:Nonbinding val description: String = "")

@Singleton
@Bound(value = "a", description = "interceptor")
class Advice : MethodInterceptor<Any, Any> {
    var calls = 0

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        calls++
        return context.proceed()
    }
}

@Singleton
@Bound(value = "a", description = "target")
open class Subject {
    open fun matching() = "done"

    @Bound(value = "b", description = "target")
    open fun different() = "done"
}
''')
        def advice = getBean(context, 'jakartanonbinding.Advice')
        def subject = getBean(context, 'jakartanonbinding.Subject')

        when:
        subject.matching()
        subject.different()

        then:
        advice.calls == 1
        recorded(getBeanDefinition(context, 'jakartanonbinding.Advice').annotationMetadata.getAnnotation('jakartanonbinding.Bound')) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE]
        recorded(getBeanDefinition(context, 'jakartanonbinding.Subject').annotationMetadata.getAnnotation('jakartanonbinding.Bound')) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE]

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

@Cylinders(8)
@Singleton
class Test

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Cylinders(
    val value: Int,
    @get:Nonbinding
    val description: String = ""
)
''')
        def metadata = definition.annotationMetadata

        expect:
        recorded(metadata.getAnnotation('jakartanonbindingqualifier.Cylinders')) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE]
        recorded(metadata.getAnnotation(AnnotationUtil.QUALIFIER)) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE]
        definition.declaredQualifier.qualifierAnn.memberNames == ['value'] as Set
    }

    private static List<String> recorded(AnnotationValue<?> annotation) {
        annotation.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE) as List
    }
}
