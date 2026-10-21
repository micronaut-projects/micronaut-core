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

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.NonBinding
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.visitor.VisitorContext

import java.lang.annotation.Annotation

class JakartaNonbindingMapperSpec extends AbstractTypeElementSpec {

    private static final String JAKARTA_NONBINDING = 'jakarta.enterprise.util.Nonbinding'
    private static final String MICRONAUT_NONBINDING = NonBinding.name

    private static final String JAKARTA_NONBINDING_SOURCE = '''
package jakarta.enterprise.util;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Nonbinding {}
'''

    void 'a member marked #nonBinding is ignored during interceptor selection'() {
        given:
        def files = new JavaFiles().add(JAKARTA_NONBINDING, JAKARTA_NONBINDING_SOURCE).add('test.Subject', """
package test;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(bindMembers = true)
@interface Bound {
    String value();
    @${nonBinding} String description() default "";
}

@Singleton
@Bound(value = "a", description = "interceptor")
class Advice implements MethodInterceptor<Object, Object> {
    static int calls;
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        calls++;
        return context.proceed();
    }
}

@Singleton
@Bound(value = "a", description = "target")
class Subject {
    public String matching() { return "done"; }

    @Bound(value = "b", description = "target")
    public String different() { return "done"; }
}
""")
        def context = buildContext(files)
        def adviceType = context.classLoader.loadClass('test.Advice')
        def subjectType = context.classLoader.loadClass('test.Subject')
        def subject = context.getBean(subjectType)

        when:
        subject.matching()
        subject.different()

        then: 'only the method whose bound member matches is intercepted'
        adviceType.calls == 1

        and: 'the member is listed on the binding of the interceptor and of the interception point'
        recorded(context.getBeanDefinition(adviceType).annotationMetadata.getAnnotation('test.Bound')) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE]
        recorded(context.getBeanDefinition(subjectType).annotationMetadata.getAnnotation('test.Bound')) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE]

        cleanup:
        context.close()

        where:
        nonBinding << [MICRONAUT_NONBINDING, JAKARTA_NONBINDING]
    }

    void 'a qualifier member marked #nonBinding is recorded on the qualifier and ignored by injection'() {
        given:
        def files = new JavaFiles().add(JAKARTA_NONBINDING, JAKARTA_NONBINDING_SOURCE).add('test.Holder', """
package test;

import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@interface Cylinders {
    int value();
    @${nonBinding} String description() default "";
}

interface Engine {
}

@Singleton
@Cylinders(value = 8, description = "declared")
class V8Engine implements Engine {
}

@Singleton
@Cylinders(6)
class V6Engine implements Engine {
}

@Singleton
class Holder {
    @Inject
    @Cylinders(value = 8, description = "requested")
    Engine engine;
}
""")
        def context = buildContext(files)
        def definition = context.getBeanDefinition(context.classLoader.loadClass('test.V8Engine'))

        expect:
        recorded(definition.annotationMetadata.getAnnotation('test.Cylinders')) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE]
        recorded(definition.annotationMetadata.getAnnotation(AnnotationUtil.QUALIFIER)) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE]
        context.getBean(context.classLoader.loadClass('test.Holder')).engine.class.simpleName == 'V8Engine'

        cleanup:
        context.close()

        where:
        nonBinding << [MICRONAUT_NONBINDING, JAKARTA_NONBINDING]
    }

    void 'a member marked #nonBinding on an annotation nested in a binding member still binds'() {
        given: 'only the members of the binding annotation itself are left out, as for a CDI interceptor binding'
        def files = new JavaFiles().add(JAKARTA_NONBINDING, JAKARTA_NONBINDING_SOURCE).add('test.Subject', """
package test;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@interface Detail {
    int id();
    @${nonBinding} String note() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(bindMembers = true)
@interface Bound {
    Detail detail();
}

@Singleton
@Bound(detail = @Detail(id = 1, note = "interceptor"))
class Advice implements MethodInterceptor<Object, Object> {
    static int calls;
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        calls++;
        return context.proceed();
    }
}

@Singleton
class Subject {
    @Bound(detail = @Detail(id = 1, note = "interceptor"))
    public String same() { return "done"; }

    @Bound(detail = @Detail(id = 1, note = "target"))
    public String otherNote() { return "done"; }
}
""")
        def context = buildContext(files)
        def adviceType = context.classLoader.loadClass('test.Advice')
        def subject = context.getBean(context.classLoader.loadClass('test.Subject'))
        AnnotationValue<?> detail = context.getBeanDefinition(adviceType).annotationMetadata
            .getAnnotation('test.Bound').getAnnotation('detail').get()

        when:
        subject.same()
        subject.otherNote()

        then: 'the nested annotation lists no members, and is compared as a whole'
        recorded(detail).isEmpty()
        adviceType.calls == 1

        cleanup:
        context.close()

        where:
        nonBinding << [MICRONAUT_NONBINDING, JAKARTA_NONBINDING]
    }

    void 'a second mapper for the Jakarta annotation, as a module still shipping its own adds, records the member once'() {
        given: 'SecondNonbindingMapper is registered for this test module next to the one of the core processor'
        SecondNonbindingMapper.calls = 0
        def files = new JavaFiles().add(JAKARTA_NONBINDING, JAKARTA_NONBINDING_SOURCE).add('test.Test', '''
package test;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@interface Cylinders {
    int value();
    @Nonbinding String description() default "";
}

@Singleton
@Cylinders(8)
class Test {
}
''')
        def context = buildContext(files)
        def definition = context.getBeanDefinition(context.classLoader.loadClass('test.Test'))

        expect: 'both mappers ran, and the member is listed once'
        SecondNonbindingMapper.calls > 0
        recorded(definition.annotationMetadata.getAnnotation('test.Cylinders')) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE]
        recorded(definition.annotationMetadata.getAnnotation(AnnotationUtil.QUALIFIER)) == ['description', AnnotationUtil.NON_BINDING_ATTRIBUTE]

        cleanup:
        context.close()
    }

    private static List<String> recorded(AnnotationValue<?> annotation) {
        annotation.stringValues(AnnotationUtil.NON_BINDING_ATTRIBUTE) as List
    }

    /**
     * A mapper of the Jakarta annotation like the ones Micronaut Jakarta Interceptors and Micronaut CDI ship.
     */
    static class SecondNonbindingMapper implements NamedAnnotationMapper {

        static int calls

        @Override
        String getName() {
            return JAKARTA_NONBINDING
        }

        @Override
        List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
            calls++
            return List.of(AnnotationValue.builder(NonBinding).build())
        }
    }
}
