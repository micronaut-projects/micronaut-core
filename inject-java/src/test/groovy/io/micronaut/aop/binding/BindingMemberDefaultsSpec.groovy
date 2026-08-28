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
import io.micronaut.core.annotation.AnnotationValue

/**
 * An interceptor binding whose members take part in the binding is compared by the members the annotation has,
 * the defaulted ones included.
 */
class BindingMemberDefaultsSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package bindingdefaults;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.ArrayList;
import java.util.List;

// no @Around: an annotation carrying both @Around and @InterceptorBinding(bindMembers = true) records two
// interceptor bindings, and the one @Around produces carries no members and so matches anything
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.AROUND, bindMembers = true)
@interface Zone {
    String value() default "north";
}

@Singleton
@Zone("north")
class NorthInterceptor implements MethodInterceptor<Object, Object> {

    static final List<String> INTERCEPTED = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        INTERCEPTED.add(context.getTarget().getClass().getSimpleName());
        return context.proceed();
    }
}

@Singleton
@Zone
class DefaultedTarget {
    String work() {
        return "done";
    }
}

@Singleton
@Zone("north")
class DeclaredTarget {
    String work() {
        return "done";
    }
}

@Singleton
@Zone("south")
class OtherTarget {
    String work() {
        return "done";
    }
}
'''

    void 'a defaulted member and the default written down are one binding'() {
        given:
        def context = buildContext(SOURCE)
        def intercepted = context.classLoader.loadClass('bindingdefaults.NorthInterceptor').INTERCEPTED
        intercepted.clear()

        when: 'a target declaring the default, one writing it down, and one declaring something else are called'
        ['DefaultedTarget', 'DeclaredTarget', 'OtherTarget'].each {
            def bean = context.getBean(context.classLoader.loadClass("bindingdefaults.$it"))
            bean.work()
        }

        then: 'the interceptor bound to @Zone("north") ran on the first two and not on the third'
        intercepted.findAll { it.contains('DefaultedTarget') }.size() == 1
        intercepted.findAll { it.contains('DeclaredTarget') }.size() == 1
        intercepted.findAll { it.contains('OtherTarget') }.isEmpty()

        cleanup:
        context.close()
    }

    void 'the values recorded for an element are the ones it declared'() {
        given:
        def context = buildContext(SOURCE)

        expect: 'nothing about what is generated changed, so metadata of an earlier version compares alike'
        bindingValues(context, 'bindingdefaults.DefaultedTarget').first().getValues().isEmpty()
        bindingValues(context, 'bindingdefaults.DeclaredTarget').first().stringValue().get() == 'north'

        cleanup:
        context.close()
    }

    private static List<AnnotationValue<?>> bindingValues(context, String type) {
        def definition = context.getBeanDefinition(context.classLoader.loadClass(type))
        definition.getAnnotationMetadata()
                .getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING)
                .findAll { it.stringValue().isPresent() }
                .collect { it.getAnnotation('$bindingValues').orElse(null) }
                .findAll { it != null }
    }
}
