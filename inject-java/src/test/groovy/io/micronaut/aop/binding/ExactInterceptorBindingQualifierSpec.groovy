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
import io.micronaut.aop.ExactInterceptorBindingQualifier
import io.micronaut.aop.Intercepted
import io.micronaut.aop.InterceptorKind
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationValue

class ExactInterceptorBindingQualifierSpec extends AbstractTypeElementSpec {

    void 'repeatable occurrences matched as one group select the beans whose every occurrence is at the point: #tags'() {
        given:
        def context = buildContext(SOURCE)
        def point = tags.collect { tag(context, it) }

        expect: "the handlers are plain beans, not interceptors"
        names(context, ExactInterceptorBindingQualifier.ofOccurrences(point, InterceptorKind.AROUND)) == expected as Set

        cleanup:
        context.close()

        where:
        tags            | expected
        ['a']           | ['TagA']
        ['a', 'b']      | ['TagA', 'TagAB']
        ['a', 'b', 'c'] | ['TagA', 'TagAB']
        ['b', 'c']      | []
        ['c', 'd']      | ['TagCD']
        []              | []
    }

    void 'the point read from metadata selects what the registry selects, the method replacing its class: #method'() {
        given:
        def context = buildContext(SOURCE)
        def metadata = context.getBeanDefinition(context.classLoader.loadClass('exactbinding.Point'))
            .getRequiredMethod(method).annotationMetadata

        expect:
        names(context, ExactInterceptorBindingQualifier.of(metadata, InterceptorKind.AROUND)) == expected as Set

        cleanup:
        context.close()

        where:
        method      | expected
        'inherited' | ['TagA', 'TagAB', 'ByName']
        'narrowed'  | ['TagA', 'ByName']
        'other'     | ['ByName']
    }

    void 'a non-binding member listed on one side only takes no part'() {
        given:
        def context = buildContext(SOURCE)
        def notedType = context.classLoader.loadClass('exactbinding.Noted')
        def built = AnnotationValue.builder(notedType).value('one').member('note', 'other').build()
        def compiled = context.getBeanDefinition(context.classLoader.loadClass('exactbinding.NotedOne'))
            .getAnnotationValuesByType(notedType)

        expect: "the point is built at runtime and carries no list of the non-binding members"
        names(context, ExactInterceptorBindingQualifier.ofOccurrences([built], InterceptorKind.AROUND)) == ['NotedOne'] as Set

        and: "a binding member still decides"
        names(context, ExactInterceptorBindingQualifier.ofOccurrences(
            [AnnotationValue.builder(notedType).value('two').member('note', 'first').build()], InterceptorKind.AROUND)) == [] as Set

        and: "the two sides match each other both ways"
        compiled.size() == 1
        compiled[0].matches(built)
        built.matches(compiled[0])

        cleanup:
        context.close()
    }

    void 'an occurrence built at runtime leaving a defaulted member out matches one compiled from the default: #zone'() {
        given:
        def context = buildContext(SOURCE)
        def builder = AnnotationValue.builder(context.classLoader.loadClass('exactbinding.Zoned'))
        def point = zone == null ? builder.build() : builder.value(zone).build()

        expect: "the built occurrence carries no defaults, and those of the compiled one apply to it"
        names(context, ExactInterceptorBindingQualifier.ofOccurrences([point], InterceptorKind.AROUND)) == expected as Set

        cleanup:
        context.close()

        where:
        zone    | expected
        null    | ['NorthZoned']
        'north' | ['NorthZoned']
        'south' | ['SouthZoned']
    }

    void 'enum, class and array members are compared: #method'() {
        given:
        def context = buildContext(SOURCE)
        def metadata = context.getBeanDefinition(context.classLoader.loadClass('exactbinding.Members'))
            .getRequiredMethod(method).annotationMetadata

        expect:
        names(context, ExactInterceptorBindingQualifier.of(metadata, InterceptorKind.AROUND)) == expected as Set

        cleanup:
        context.close()

        where:
        method          | expected
        'scoped'        | ['ScopedX']
        'scopedY'       | []
        'high'          | ['High']
        'low'           | []
        'stringTyped'   | ['StringTyped']
        'integerTyped'  | []
        'arrayed'       | ['Arrayed']
        'reordered'     | []
    }

    void 'the candidate given as occurrences must have every one of them at the point'() {
        given:
        def context = buildContext(SOURCE)
        def qualifier = ExactInterceptorBindingQualifier.ofOccurrences([tag(context, 'a'), tag(context, 'b')], InterceptorKind.AROUND)

        expect: "the candidate carries no Micronaut binding, its occurrences are compared as one group"
        qualifier.matches([tag(context, 'a')])
        qualifier.matches([tag(context, 'a'), tag(context, 'b')])
        !qualifier.matches([tag(context, 'a'), tag(context, 'c')])
        !qualifier.matches([tag(context, 'c')])

        and: "an annotation the point does not have, or no occurrence at all, does not qualify"
        !qualifier.matches([tag(context, 'a'), AnnotationValue.builder(context.classLoader.loadClass('exactbinding.Labeled')).build()])
        !qualifier.matches([])

        cleanup:
        context.close()
    }

    void 'the candidate given as occurrences is compared with a point read from metadata'() {
        given:
        def context = buildContext(SOURCE)
        def point = context.getBeanDefinition(context.classLoader.loadClass('exactbinding.Point'))
        def qualifier = ExactInterceptorBindingQualifier.of(point.getRequiredMethod('inherited').annotationMetadata, InterceptorKind.AROUND)
        def compiled = context.getBeanDefinition(context.classLoader.loadClass('exactbinding.TagAB'))
            .getAnnotationValuesByType(context.classLoader.loadClass('exactbinding.Tag'))

        expect: "the compiled occurrences of another bean, and a binding the point has by name only"
        compiled.size() == 2
        qualifier.matches(compiled)
        qualifier.matches([AnnotationValue.builder(context.classLoader.loadClass('exactbinding.Labeled')).value('any').build()])
        !qualifier.matches(compiled + [tag(context, 'c')])

        cleanup:
        context.close()
    }

    void 'a nested non-binding member takes no part when the candidate is given as occurrences'() {
        given:
        def context = buildContext(SOURCE)
        def scopedType = context.classLoader.loadClass('exactbinding.Scoped')
        def scopeType = context.classLoader.loadClass('exactbinding.Scope')
        def point = context.getBeanDefinition(context.classLoader.loadClass('exactbinding.Members'))
            .getRequiredMethod('scoped').annotationMetadata
        def built = AnnotationValue.builder(scopedType)
            .values(AnnotationValue.builder(scopeType).value('x').member('note', 'built').build())
            .build()

        expect:
        ExactInterceptorBindingQualifier.of(point, InterceptorKind.AROUND).matches([built])
        !ExactInterceptorBindingQualifier.of(point, InterceptorKind.AROUND).matches([
            AnnotationValue.builder(scopedType).values(AnnotationValue.builder(scopeType).value('y').build()).build()])

        cleanup:
        context.close()
    }

    void 'the qualifier is equal to one built from the same point'() {
        given:
        def context = buildContext(SOURCE)
        def a = tag(context, 'a')
        def b = tag(context, 'b')

        expect:
        ExactInterceptorBindingQualifier.ofOccurrences([a, b], InterceptorKind.AROUND) ==
            ExactInterceptorBindingQualifier.ofOccurrences([a, b], InterceptorKind.AROUND)
        ExactInterceptorBindingQualifier.ofOccurrences([a, b], InterceptorKind.AROUND).hashCode() ==
            ExactInterceptorBindingQualifier.ofOccurrences([a, b], InterceptorKind.AROUND).hashCode()
        ExactInterceptorBindingQualifier.ofOccurrences([a], InterceptorKind.AROUND) !=
            ExactInterceptorBindingQualifier.ofOccurrences([a, b], InterceptorKind.AROUND)

        cleanup:
        context.close()
    }

    private static AnnotationValue<?> tag(ApplicationContext context, String value) {
        return AnnotationValue.builder(context.classLoader.loadClass('exactbinding.Tag')).value(value).build()
    }

    private static Set<String> names(ApplicationContext context, ExactInterceptorBindingQualifier<Object> qualifier) {
        def handlerType = context.classLoader.loadClass('exactbinding.Handler')
        return context.getBeansOfType(handlerType, qualifier)
            .collect { (it instanceof Intercepted ? it.getClass().superclass : it.getClass()).simpleName } as Set
    }

    private static final String SOURCE = '''
package exactbinding;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.NonBinding;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@Repeatable(Tags.class)
@InterceptorBinding(bindMembers = true, matchAllOccurrences = true)
@interface Tag {
    String value();
    @NonBinding String description() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@interface Tags {
    Tag[] value();
}

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding
@interface Labeled {
    String value() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(bindMembers = true)
@interface Noted {
    String value();
    @NonBinding String note() default "";
}

enum Level { HIGH, LOW }

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(bindMembers = true)
@interface Leveled {
    Level value();
}

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(bindMembers = true)
@interface Typed {
    Class<?> value();
}

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(bindMembers = true)
@interface Listed {
    String[] value();
}

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(bindMembers = true)
@interface Zoned {
    String value() default "north";
}

@Retention(RetentionPolicy.RUNTIME)
@interface Scope {
    String value();
    @NonBinding String note() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(bindMembers = true)
@interface Scoped {
    Scope[] value();
}

interface Handler {
}

@Singleton
@Scoped(@Scope(value = "x", note = "handler"))
class ScopedX implements Handler {
}

@Singleton
@Zoned
class NorthZoned implements Handler {
}

@Singleton
@Zoned("south")
class SouthZoned implements Handler {
}

@Singleton
@Tag(value = "a", description = "only a")
class TagA implements Handler {
}

@Singleton
@Tag("a")
@Tag("b")
class TagAB implements Handler {
}

@Singleton
@Tag("c")
@Tag("d")
class TagCD implements Handler {
}

@Singleton
@Labeled("anything")
class ByName implements Handler {
}

@Singleton
class Unbound implements Handler {
}

@Singleton
@Noted(value = "one", note = "first")
class NotedOne implements Handler {
}

@Singleton
@Leveled(Level.HIGH)
class High implements Handler {
}

@Singleton
@Typed(String.class)
class StringTyped implements Handler {
}

@Singleton
@Listed({"x", "y"})
class Arrayed implements Handler {
}

@Singleton
@Tag(value = "a", description = "point")
@Tag("b")
@Labeled
class Point {
    public void inherited() {
    }

    @Tag("a")
    public void narrowed() {
    }

    @Tag("z")
    public void other() {
    }
}

@Singleton
class Members {
    @Scoped(@Scope(value = "x", note = "point"))
    public void scoped() {
    }

    @Scoped(@Scope("y"))
    public void scopedY() {
    }

    @Leveled(Level.HIGH)
    public void high() {
    }

    @Leveled(Level.LOW)
    public void low() {
    }

    @Typed(String.class)
    public void stringTyped() {
    }

    @Typed(Integer.class)
    public void integerTyped() {
    }

    @Listed({"x", "y"})
    public void arrayed() {
    }

    @Listed({"y", "x"})
    public void reordered() {
    }
}
'''
}
