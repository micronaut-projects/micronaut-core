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
import io.micronaut.core.annotation.AnnotationBuilder
import io.micronaut.core.annotation.AnnotationBuilderRegistry
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class AnnotationBuilderRegistrationSpec extends AbstractKotlinCompilerSpec {

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
        def classLoader = buildClassLoader("ktvisitortest.First", '''
package ktvisitortest

annotation class WantsBuilder

enum class Level { LOW, HIGH }

annotation class Leaf(val value: Int = 7)

annotation class Inner(val name: String, val leaves: Array<Leaf> = [Leaf()])

@WantsBuilder
annotation class Outer(val value: Array<Inner>, val level: Level = Level.LOW)

// KSP reads the defaults of an annotation type from a use of it on an element, not in a member
@Leaf
@Outer([Inner("a", [Leaf(1), Leaf(2)]), Inner("b")])
class First

@Outer([Inner("c")], level = Level.HIGH)
class Second {
    @Outer([Inner("d")])
    fun method() {
    }
}
''')

        when:
        def outerType = classLoader.loadClass("ktvisitortest.Outer")
        def jvm = classLoader.loadClass("ktvisitortest.First").getAnnotation(outerType)
        def outer = builder(classLoader, "ktvisitortest.First", "ktvisitortest.Outer")
        def inner = builder(classLoader, "ktvisitortest.First", "ktvisitortest.Inner")
        def leaf = builder(classLoader, "ktvisitortest.First", "ktvisitortest.Leaf")
        Map<String, Object> members = outer.toMap(jvm)

        then: "a builder per owner, however many times its elements register it"
        RegisteringVisitor.REGISTERED.count { it == "ktvisitortest.Second:ktvisitortest.Outer" } == 2
        hasBuilder(classLoader, "ktvisitortest.Second", "ktvisitortest.Outer")
        hasBuilder(classLoader, "ktvisitortest.Second", "ktvisitortest.Leaf")
        !hasBuilder(classLoader, "ktvisitortest.First", "ktvisitortest.WantsBuilder")

        and: "the members are typed"
        members.keySet() as List == ["value", "level"]
        members.level.name() == "LOW"
        members.level.class.name == "ktvisitortest.Level"
        members.value.class == inner.annotationType().arrayType()
        members.value*.name() == ["a", "b"]
        inner.toMap(members.value[0]).leaves.class == leaf.annotationType().arrayType()
        leaf.toMap(members.value[1].leaves()[0]) == [value: 7]
        leaf.build([:]).value() == 7

        and: "the built annotations equal those of the JVM"
        outer.build(members) == jvm
        outer.build(members).hashCode() == jvm.hashCode()
    }

    void "a visitor registers the builder of an annotation of a library"() {
        given:
        def classLoader = buildClassLoader("ktvisitortest.Library", '''
package ktvisitortest

@jakarta.inject.Named("library")
class Library
''')

        when:
        def named = builder(classLoader, "ktvisitortest.Library", "jakarta.inject.Named")
        def jvm = classLoader.loadClass("ktvisitortest.Library").getAnnotation(jakarta.inject.Named)

        then:
        named.build([value: "library"]) == jvm
        named.toMap(jvm) == [value: "library"]
    }

    void "an annotation that is not public cannot have a builder"() {
        when:
        buildClassLoader("ktvisitortest.Hidden", '''
package ktvisitortest

annotation class WantsBuilder

@WantsBuilder
private annotation class Private

@Private
class Hidden
''')

        then:
        def e = thrown(Throwable)
        stackMessages(e).contains("The annotation [ktvisitortest.Private] cannot have a builder: the type ktvisitortest.Private is not public")
    }

    private static String stackMessages(Throwable e) {
        def messages = new StringBuilder()
        for (Throwable t = e; t != null; t = t.cause) {
            messages.append(t.message).append('\n')
        }
        return messages.toString()
    }

    /**
     * Registers the builders of the annotations of the {@code ktvisitortest} package whose type is annotated with
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
            if (owner.packageName != "ktvisitortest") {
                return
            }
            for (String name : element.declaredAnnotationNames) {
                ClassElement annotationType = context.getClassElement(name).orElse(null)
                if (annotationType != null && (name == "jakarta.inject.Named" || annotationType.hasDeclaredAnnotation("ktvisitortest.WantsBuilder"))) {
                    context.registerAnnotationBuilder(annotationType, element)
                    context.registerAnnotationBuilder(annotationType, element, element)
                    REGISTERED.add(owner.name + ":" + name)
                }
            }
        }
    }
}
