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
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class RegisterAnnotationBuilderSpec extends AbstractBeanDefinitionSpec {

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
        def classLoader = buildClassLoader('''
package groovybuildertest

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@interface WantsBuilder {
}

@WantsBuilder
@Retention(RetentionPolicy.RUNTIME)
@interface Custom {
    String value() default "none"
    int weight() default 1
}

@Custom("first")
class First {
}

@Custom("second")
class Second {
    @Custom("method")
    void method() {
    }
}
''')

        when:
        def holders = ["groovybuildertest.First", "groovybuildertest.Second"].findAll { hasBuilder(classLoader, it, "groovybuildertest.Custom") }
        def builder = builder(classLoader, holders[0], "groovybuildertest.Custom")
        def custom = builder.build([value: "built"])
        def jvm = classLoader.loadClass("groovybuildertest.First").getAnnotation(custom.annotationType())

        then:
        RequestingVisitor.RESULTS == ["groovybuildertest.Custom": true]
        holders == ["groovybuildertest.First", "groovybuildertest.Second"]
        custom.value() == "built"
        custom.weight() == 1
        builder.build([value: "first"]) == jvm
    }

    void "a visitor requests a builder for an annotation of a library"() {
        given:
        def classLoader = buildClassLoader('''
package groovybuildertest

@jakarta.inject.Named("library")
class Named {
}
''')

        when:
        def builder = builder(classLoader, "groovybuildertest.Named", "jakarta.inject.Named")
        def jvm = classLoader.loadClass("groovybuildertest.Named").getAnnotation(jakarta.inject.Named)

        then:
        RequestingVisitor.RESULTS == ["jakarta.inject.Named": true]
        builder.build([value: "built"]).value() == "built"
        builder.build([value: "library"]) == jvm
    }

    void "a request and @RegisterAnnotations of the same type and annotation write one builder"() {
        given:
        def classLoader = buildClassLoader('''
package groovybuildertest

import io.micronaut.core.annotation.RegisterAnnotations
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@interface WantsBuilder {
}

@WantsBuilder
@Retention(RetentionPolicy.RUNTIME)
@interface Listed {
    String value()
}

@Listed("both")
@RegisterAnnotations(Listed)
class Both {
}
''')

        expect:
        RequestingVisitor.RESULTS == ["groovybuildertest.Listed": true]
        builder(classLoader, "groovybuildertest.Both", "groovybuildertest.Listed").build([value: "x"]).value() == "x"
    }

    void "an annotation type that is not public has no builder"() {
        given:
        def classLoader = buildClassLoader('''
package groovybuildertest

import groovy.transform.PackageScope
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@interface WantsBuilder {
}

@WantsBuilder
@PackageScope
@Retention(RetentionPolicy.RUNTIME)
@interface Private {
}

@Private
class Hidden {
}
''')

        expect:
        RequestingVisitor.RESULTS == ["groovybuildertest.Private": false]
        !hasBuilder(classLoader, "groovybuildertest.Hidden", "groovybuildertest.Private")
    }

    /**
     * Requests a builder for each annotation of the package {@code groovybuildertest} that is annotated with
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
            return "groovybuildertest"
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
}
