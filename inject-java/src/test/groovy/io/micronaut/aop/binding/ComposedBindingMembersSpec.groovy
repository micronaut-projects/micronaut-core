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

/**
 * Two annotations composing the same binding annotation with different members contribute two occurrences to one
 * element. The flat, name-keyed index keeps one of them; the binding annotation is retained on each composing
 * annotation, so each occurrence is compared.
 */
class ComposedBindingMembersSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package composedbinding;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.ArrayList;
import java.util.List;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@InterceptorBinding(kind = InterceptorKind.AROUND, bindMembers = true)
@interface Zone {
    String value();
}

@Zone("north")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface NorthZone {
}

@Zone("south")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface SouthZone {
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
@Zone("south")
class SouthInterceptor implements MethodInterceptor<Object, Object> {

    static final List<String> INTERCEPTED = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        INTERCEPTED.add(context.getTarget().getClass().getSimpleName());
        return context.proceed();
    }
}

@Singleton
@NorthZone
@SouthZone
class BothTarget {
    String work() {
        return "done";
    }
}

@Singleton
@NorthZone
class NorthTarget {
    String work() {
        return "done";
    }
}
'''

    void 'an occurrence composed by another annotation is compared on its own members'() {
        given:
        def context = buildContext(SOURCE)
        def north = context.classLoader.loadClass('composedbinding.NorthInterceptor').INTERCEPTED
        def south = context.classLoader.loadClass('composedbinding.SouthInterceptor').INTERCEPTED
        north.clear()
        south.clear()

        when:
        ['BothTarget', 'NorthTarget'].each {
            context.getBean(context.classLoader.loadClass("composedbinding.$it")).work()
        }

        then: 'the element composing both zones is intercepted by both, the one composing north only by north'
        north.count { it.contains('BothTarget') } == 1
        south.count { it.contains('BothTarget') } == 1
        north.count { it.contains('NorthTarget') } == 1
        south.count { it.contains('NorthTarget') } == 0

        and: 'which the flat index alone could not tell, since it keeps one Zone for the element'
        def metadata = context.getBeanDefinition(context.classLoader.loadClass('composedbinding.BothTarget'))
                .getAnnotationMetadata()
        metadata.getAnnotation('composedbinding.Zone') != null
        ['composedbinding.NorthZone', 'composedbinding.SouthZone'].collect {
            metadata.getAnnotation(it).getStereotypes().find { it.getAnnotationName() == 'composedbinding.Zone' }.stringValue().get()
        } == ['north', 'south']

        cleanup:
        context.close()
    }
}
