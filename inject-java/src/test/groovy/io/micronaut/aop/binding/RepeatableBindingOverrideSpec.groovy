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
package io.micronaut.aop.binding

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier

class RepeatableBindingOverrideSpec extends AbstractTypeElementSpec {

    void 'the occurrences declared on a method replace those of its class only when opted in: #all, #tags'() {
        given:
        def context = buildContext("""
package repeatableoverride;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(Tags.class)
@Around
@InterceptorBinding(bindMembers = true, matchAllOccurrences = $all)
@interface Tag {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@interface Tags {
    Tag[] value();
}

@Singleton
@Tag("a")
class AInterceptor implements MethodInterceptor<Object, Object> {
    static int calls;
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        calls++;
        return context.proceed();
    }
}

@Singleton
@Tag("b")
class BInterceptor implements MethodInterceptor<Object, Object> {
    static int calls;
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        calls++;
        return context.proceed();
    }
}

@Singleton
@Tag("a")
class TargetBean {
    ${tags.collect { '@Tag("' + it + '")' }.join('\n')}
    public String work() {
        return "done";
    }
}
""")
        def targetType = context.classLoader.loadClass('repeatableoverride.TargetBean')
        def method = context.getBeanDefinition(targetType).getRequiredMethod('work')
        def binding = method.annotationMetadata.getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING)
            .find { it.stringValue().orElse('') == 'repeatableoverride.Tag' }

        when:
        context.getBean(targetType).work()

        then:
        context.classLoader.loadClass('repeatableoverride.AInterceptor').calls == aCalls
        context.classLoader.loadClass('repeatableoverride.BInterceptor').calls == bCalls
        InterceptorBindingQualifier.resolveBoundOccurrences(binding, method.annotationMetadata, true)
            .collect { it.stringValue().get() }.toSet() == effective.toSet()

        cleanup:
        context.close()

        where:
        all      | tags       | aCalls | bCalls | effective
        false    | ['b']      | 1      | 1      | ['a', 'b']
        true     | ['b']      | 0      | 1      | ['b']
        true     | ['b', 'c'] | 0      | 1      | ['b', 'c']
        false    | []         | 1      | 0      | ['a']
        true     | []         | 1      | 0      | ['a']
    }
}
