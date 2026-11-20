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
import io.micronaut.core.annotation.AnnotationBuilder
import io.micronaut.core.annotation.AnnotationBuilderRegistry
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class AnnotationBuilderRegistrationSpec extends AbstractBeanDefinitionSpec {

    void setup() {
        RegisteringVisitor.REGISTERED.clear()
    }

    private static AnnotationBuilder builder(ClassLoader classLoader, String holder, String annotation) {
        String name = holder + '$' + AnnotationBuilderRegistry.mangle(annotation) + AnnotationBuilderRegistry.BUILDER_SUFFIX
        return (AnnotationBuilder) classLoader.loadClass(name).getDeclaredConstructor().newInstance()
    }

    private static boolean hasBuilder(ClassLoader classLoader, String holder, String annotation) {
        try {
            builder(classLoader, holder, annotation)
            return true
        } catch (ClassNotFoundException ignored) {
            return false
        }
    }

    void "a visitor registers the builders of an annotation and of the annotation types of its members"() {
        given:
        def classLoader = buildClassLoader('''
package groovyvisitortest

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@interface WantsBuilder {
}

enum Level { LOW, HIGH }

@Retention(RetentionPolicy.RUNTIME)
@interface Leaf {
    int value() default 7
}

@Retention(RetentionPolicy.RUNTIME)
@interface Inner {
    String name()
    Leaf[] leaves() default [@Leaf]
}

@WantsBuilder
@Retention(RetentionPolicy.RUNTIME)
@interface Outer {
    Inner[] value()
    Level level() default Level.LOW
}

@Outer([@Inner(name = "a", leaves = [@Leaf(1), @Leaf(2)]), @Inner(name = "b", leaves = [@Leaf])])
class First {
}

@Outer(value = [@Inner(name = "c")], level = Level.HIGH)
class Second {
    @Outer([@Inner(name = "d")])
    void method() {
    }
}

@jakarta.inject.Named("library")
class Library {
}
''')

        when:
        def outerType = classLoader.loadClass("groovyvisitortest.Outer")
        def jvm = classLoader.loadClass("groovyvisitortest.First").getAnnotation(outerType)
        def outer = builder(classLoader, "groovyvisitortest.First", "groovyvisitortest.Outer")
        def inner = builder(classLoader, "groovyvisitortest.First", "groovyvisitortest.Inner")
        def leaf = builder(classLoader, "groovyvisitortest.First", "groovyvisitortest.Leaf")
        def named = builder(classLoader, "groovyvisitortest.Library", "jakarta.inject.Named")
        Map<String, Object> members = outer.toMap(jvm)

        then: "a builder per owner, however many times its elements register it"
        RegisteringVisitor.REGISTERED.count { it == "groovyvisitortest.Second:groovyvisitortest.Outer" } == 2
        hasBuilder(classLoader, "groovyvisitortest.Second", "groovyvisitortest.Outer")
        hasBuilder(classLoader, "groovyvisitortest.Second", "groovyvisitortest.Leaf")
        !hasBuilder(classLoader, "groovyvisitortest.First", "groovyvisitortest.WantsBuilder")

        and: "the members are typed"
        members.keySet() as List == ["value", "level"]
        members.level.name() == "LOW"
        members.level.class.name == "groovyvisitortest.Level"
        members.value.class == inner.annotationType().arrayType()
        members.value*.name() == ["a", "b"]
        inner.toMap(members.value[0]).leaves.class == leaf.annotationType().arrayType()
        leaf.toMap(members.value[1].leaves()[0]) == [value: 7]
        leaf.build([:]).value() == 7

        and: "the built annotations equal those of the JVM"
        outer.build(members) == jvm
        outer.build(members).hashCode() == jvm.hashCode()

        and: "an annotation of a library has a builder"
        named.toMap(classLoader.loadClass("groovyvisitortest.Library").getAnnotation(jakarta.inject.Named)) == [value: "library"]
    }

    /**
     * Registers the builders of the annotations of the {@code groovyvisitortest} package whose type is annotated with
     * {@code WantsBuilder}, and of {@code jakarta.inject.Named}; every element registers twice, the second time with
     * the origin repeated.
     */
    static class RegisteringVisitor implements TypeElementVisitor<Object, Object> {

        static final List<String> REGISTERED = Collections.synchronizedList(new ArrayList<>())

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            register(element, element, context)
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            register(element, element.owningType, context)
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        private static void register(Element element, ClassElement owner, VisitorContext context) {
            if (owner.packageName != "groovyvisitortest") {
                return
            }
            for (String name : element.declaredAnnotationNames) {
                ClassElement annotationType = context.getClassElement(name).orElse(null)
                if (annotationType != null && (name == "jakarta.inject.Named" || annotationType.hasDeclaredAnnotation("groovyvisitortest.WantsBuilder"))) {
                    context.registerAnnotationBuilder(annotationType, element)
                    context.registerAnnotationBuilder(annotationType, element, element)
                    REGISTERED.add(owner.name + ":" + name)
                }
            }
        }
    }
}
