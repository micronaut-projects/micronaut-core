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
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class RegisterAnnotationBuilderSpec extends AbstractKotlinCompilerSpec {

    void setup() {
        RequestingVisitor.RESULTS.clear()
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

    void "an isolating visitor gets a builder next to each type that uses the annotation"() {
        given:
        def classLoader = buildClassLoader("kotlinbuildertest.First", '''
package kotlinbuildertest

annotation class WantsBuilder

@WantsBuilder
annotation class Custom(val value: String = "none", val weight: Int = 1)

@Custom("first")
class First

@Custom("second")
class Second {
    @Custom("method")
    fun method() {
    }
}
''')

        when:
        def holders = ["kotlinbuildertest.First", "kotlinbuildertest.Second"].findAll { hasBuilder(classLoader, it, "kotlinbuildertest.Custom") }
        def builder = builder(classLoader, holders[0], "kotlinbuildertest.Custom")
        def custom = builder.build([value: "built"])
        def jvm = classLoader.loadClass("kotlinbuildertest.First").getAnnotation(custom.annotationType())

        then:
        RequestingVisitor.RESULTS == ["kotlinbuildertest.Custom": true]
        holders == ["kotlinbuildertest.First", "kotlinbuildertest.Second"]
        custom.value() == "built"
        custom.weight() == 1
        builder.build([value: "first"]) == jvm
    }

    void "the requests of aggregating visitors share one builder in the compilation"() {
        given:
        def classLoader = buildClassLoader("aggregatingkotlinbuildertest.First", '''
package aggregatingkotlinbuildertest

annotation class WantsBuilder

@WantsBuilder
annotation class Custom(val value: String = "none", val weight: Int = 1)

@Custom("first")
class First

@Custom("second")
class Second {
    @Custom("method")
    fun method() {
    }
}
''')

        when:
        def holders = ["aggregatingkotlinbuildertest.First", "aggregatingkotlinbuildertest.Second"].findAll { hasBuilder(classLoader, it, "aggregatingkotlinbuildertest.Custom") }
        def builder = builder(classLoader, holders[0], "aggregatingkotlinbuildertest.Custom")
        def custom = builder.build([value: "built"])
        def jvm = classLoader.loadClass("aggregatingkotlinbuildertest.First").getAnnotation(custom.annotationType())

        then:
        RequestingVisitor.RESULTS == ["aggregatingkotlinbuildertest.Custom": true]
        holders.size() == 1
        custom.value() == "built"
        custom.weight() == 1
        builder.build([value: "first"]) == jvm
    }

    void "a visitor requests a builder for an annotation of a library"() {
        given:
        def classLoader = buildClassLoader("kotlinbuildertest.Named", '''
package kotlinbuildertest

@jakarta.inject.Named("library")
class Named
''')

        when:
        def builder = builder(classLoader, "kotlinbuildertest.Named", "jakarta.inject.Named")
        def jvm = classLoader.loadClass("kotlinbuildertest.Named").getAnnotation(jakarta.inject.Named)

        then:
        RequestingVisitor.RESULTS == ["jakarta.inject.Named": true]
        builder.build([value: "built"]).value() == "built"
        builder.build([value: "library"]) == jvm
    }

    void "a request and @RegisterAnnotations of the same type and annotation write one builder"() {
        given:
        def classLoader = buildClassLoader("kotlinbuildertest.Both", '''
package kotlinbuildertest

import io.micronaut.core.annotation.RegisterAnnotations

annotation class WantsBuilder

@WantsBuilder
annotation class Listed(val value: String)

@Listed("both")
@RegisterAnnotations(Listed::class)
class Both
''')

        expect:
        RequestingVisitor.RESULTS == ["kotlinbuildertest.Listed": true]
        builder(classLoader, "kotlinbuildertest.Both", "kotlinbuildertest.Listed").build([value: "x"]).value() == "x"
    }

    void "an annotation type that is not public has no builder"() {
        given:
        def classLoader = buildClassLoader("kotlinbuildertest.Hidden", '''
package kotlinbuildertest

annotation class WantsBuilder

@WantsBuilder
private annotation class Private

@Private
class Hidden
''')

        expect:
        RequestingVisitor.RESULTS == ["kotlinbuildertest.Private": false]
        !hasBuilder(classLoader, "kotlinbuildertest.Hidden", "kotlinbuildertest.Private")
    }

    /**
     * Requests a builder for each annotation of the package {@code kotlinbuildertest} that is annotated with
     * {@code WantsBuilder} of the package, and for {@code jakarta.inject.Named}.
     */
    static class RequestingVisitor implements TypeElementVisitor<Object, Object> {

        static final Map<String, Boolean> RESULTS = new LinkedHashMap<>()

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            request(element, element.packageName, context)
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            request(element, element.owningType.packageName, context)
        }

        @Override
        void visitField(FieldElement element, VisitorContext context) {
            request(element, element.owningType.packageName, context)
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }

        protected String requestingPackage() {
            return "kotlinbuildertest"
        }

        private void request(Element element, String packageName, VisitorContext context) {
            if (packageName != requestingPackage()) {
                return
            }
            for (String name : element.annotationNames) {
                ClassElement annotationType = context.getClassElement(name).orElse(null)
                if (annotationType != null && (name == "jakarta.inject.Named" || annotationType.hasDeclaredAnnotation(packageName + ".WantsBuilder"))) {
                    RESULTS.put(name, context.registerAnnotationBuilder(annotationType, element))
                }
            }
        }
    }

    /**
     * The aggregating variant of {@link RequestingVisitor}, for the package {@code aggregatingkotlinbuildertest}.
     */
    static class AggregatingRequestingVisitor extends RequestingVisitor {

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.AGGREGATING
        }

        @Override
        protected String requestingPackage() {
            return "aggregatingkotlinbuildertest"
        }
    }
}
