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
import io.micronaut.aop.InterceptorKind
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier

/**
 * An annotation carrying both {@code @Around} and {@code @InterceptorBinding} records one interceptor binding
 * per kind: the binding {@code @Around} would derive carries no members, and saying nothing is not what the
 * declared {@code @InterceptorBinding} of the same kind means, so it is not written.
 */
class AroundBindingMembersSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package aroundbindingmembers;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.ArrayList;
import java.util.List;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
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

    void 'an interceptor binding by members is bound by them when the annotation also carries @Around'() {
        given:
        def context = buildContext(SOURCE)
        def intercepted = context.classLoader.loadClass('aroundbindingmembers.NorthInterceptor').INTERCEPTED
        intercepted.clear()

        when: 'a target declaring the default, one writing it down, and one declaring something else are called'
        ['DefaultedTarget', 'DeclaredTarget', 'OtherTarget'].each {
            def bean = context.getBean(context.classLoader.loadClass("aroundbindingmembers.$it"))
            bean.work()
        }

        then: 'the interceptor bound to @Zone("north") ran on the first two and not on the third'
        intercepted.findAll { it.contains('DefaultedTarget') }.size() == 1
        intercepted.findAll { it.contains('DeclaredTarget') }.size() == 1
        intercepted.findAll { it.contains('OtherTarget') }.isEmpty()

        cleanup:
        context.close()
    }

    void 'the memberless binding @Around would derive is not recorded next to the declared one'() {
        given:
        def context = buildContext(SOURCE)

        when:
        def bindings = context.getBeanDefinition(context.classLoader.loadClass('aroundbindingmembers.OtherTarget'))
                .getAnnotationMetadata()
                .getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING)
                .findAll { it.stringValue().isPresent() }

        then: 'the single binding for the kind is the declared one, the one that binds by members'
        bindings.size() == 1
        bindings[0].enumValue('kind', InterceptorKind).get() == InterceptorKind.AROUND
        bindings[0].booleanValue('bindMembers').get()

        and: 'it resolves to the occurrence the bean declared'
        def metadata = context.getBeanDefinition(context.classLoader.loadClass('aroundbindingmembers.OtherTarget'))
                .getAnnotationMetadata()
        InterceptorBindingQualifier.resolveBoundOccurrences(bindings[0], metadata).first().stringValue().get() == 'south'

        cleanup:
        context.close()
    }

    void 'a binding of another kind still derives its own from @Around'() {
        given:
        def context = buildContext('''
package aroundbindingmemberskinds;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Both {
}

@Singleton
@Both
class BothTarget {
    String work() {
        return "done";
    }
}
''')

        when:
        def kinds = context.getBeanDefinition(context.classLoader.loadClass('aroundbindingmemberskinds.BothTarget'))
                .getAnnotationMetadata()
                .getAnnotationValuesByName(AnnotationUtil.ANN_INTERCEPTOR_BINDING)
                .findAll { it.stringValue().isPresent() }
                .collect { it.enumValue('kind', InterceptorKind).get() }

        then: 'the kinds differ, so both bindings are written'
        kinds.toSorted() == [InterceptorKind.AROUND, InterceptorKind.POST_CONSTRUCT].toSorted()

        cleanup:
        context.close()
    }
}
