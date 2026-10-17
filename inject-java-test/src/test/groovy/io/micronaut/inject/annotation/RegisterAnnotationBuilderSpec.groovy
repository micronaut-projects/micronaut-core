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
import io.micronaut.core.annotation.AnnotationBuilder
import io.micronaut.core.annotation.AnnotationBuilderRegistry
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class RegisterAnnotationBuilderSpec extends AbstractTypeElementSpec {

    void setup() {
        RequestingVisitor.RESULTS.clear()
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new RequestingVisitor(), new AggregatingRequestingVisitor(), new RegisterAnnotationsVisitor()]
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

    def "an isolating visitor gets a builder next to each type that uses the annotation"() {
        given:
        def classLoader = buildClassLoader("buildertest.First", '''
package buildertest;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface WantsBuilder {
}

@First.Custom("first")
public class First {
    @WantsBuilder
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Custom {
        String value() default "none";
        int weight() default 1;
    }
}

@First.Custom("second")
class Second {
    @First.Custom("field")
    String field;
}
''')

        when:
        def holders = ["buildertest.First", "buildertest.Second"].findAll { hasBuilder(classLoader, it, "buildertest.First\$Custom") }
        def builder = builder(classLoader, holders[0], "buildertest.First\$Custom")
        def custom = builder.build([value: "built"])
        def jvm = classLoader.loadClass("buildertest.First").getAnnotation(custom.annotationType())

        then:
        RequestingVisitor.RESULTS == ["buildertest.First\$Custom": true]
        holders == ["buildertest.First", "buildertest.Second"]
        custom.annotationType().name == "buildertest.First\$Custom"
        custom.value() == "built"
        custom.weight() == 1
        builder.build([value: "first"]) == jvm
    }

    def "the requests of aggregating visitors share one builder in the compilation"() {
        given:
        def classLoader = buildClassLoader("aggregatingbuildertest.First", '''
package aggregatingbuildertest;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface WantsBuilder {
}

@First.Custom("first")
public class First {
    @WantsBuilder
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Custom {
        String value() default "none";
        int weight() default 1;
    }
}

@First.Custom("second")
class Second {
    @First.Custom("field")
    String field;
}
''')

        when:
        def holders = ["aggregatingbuildertest.First", "aggregatingbuildertest.Second"].findAll { hasBuilder(classLoader, it, "aggregatingbuildertest.First\$Custom") }
        def builder = builder(classLoader, holders[0], "aggregatingbuildertest.First\$Custom")
        def custom = builder.build([value: "built"])
        def jvm = classLoader.loadClass("aggregatingbuildertest.First").getAnnotation(custom.annotationType())

        then:
        RequestingVisitor.RESULTS == ["aggregatingbuildertest.First\$Custom": true]
        holders.size() == 1
        custom.annotationType().name == "aggregatingbuildertest.First\$Custom"
        custom.value() == "built"
        custom.weight() == 1
        builder.build([value: "first"]) == jvm
    }

    def "a member places the builder next to its owning type"() {
        given:
        def classLoader = buildClassLoader("buildertest.Owner", '''
package buildertest;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@interface WantsBuilder {
}

public class Owner {
    @WantsBuilder
    @Retention(RetentionPolicy.RUNTIME)
    public @interface OnMember {
        String value();
    }

    @OnMember("method")
    void method() {
    }
}
''')

        expect:
        builder(classLoader, "buildertest.Owner", "buildertest.Owner\$OnMember").build([value: "x"]).value() == "x"
    }

    def "a visitor requests a builder for an annotation of a library"() {
        given:
        def classLoader = buildClassLoader("buildertest.Named", '''
package buildertest;

@jakarta.inject.Named("library")
class Named {
}
''')

        when:
        def builder = builder(classLoader, "buildertest.Named", "jakarta.inject.Named")
        def named = builder.build([value: "built"])
        def jvm = classLoader.loadClass("buildertest.Named").getAnnotation(jakarta.inject.Named)

        then:
        RequestingVisitor.RESULTS == ["jakarta.inject.Named": true]
        named instanceof jakarta.inject.Named
        named.value() == "built"
        builder.build([value: "library"]) == jvm
    }

    def "a request and @RegisterAnnotations of the same type and annotation write one builder"() {
        given:
        def classLoader = buildClassLoader("buildertest.Both", '''
package buildertest;

import io.micronaut.core.annotation.RegisterAnnotations;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@interface WantsBuilder {
}

@Both.Listed("both")
@RegisterAnnotations(Both.Listed.class)
public class Both {
    @WantsBuilder
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Listed {
        String value();
    }
}
''')

        expect:
        RequestingVisitor.RESULTS == ["buildertest.Both\$Listed": true]
        builder(classLoader, "buildertest.Both", "buildertest.Both\$Listed").build([value: "x"]).value() == "x"
    }

    def "an annotation type that is not public has no builder"() {
        given:
        def classLoader = buildClassLoader("buildertest.Hidden", '''
package buildertest;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@interface WantsBuilder {
}

@WantsBuilder
@Retention(RetentionPolicy.RUNTIME)
@interface Private {
}

@Private
class Hidden {
}
''')

        expect:
        RequestingVisitor.RESULTS == ["buildertest.Private": false]
        !hasBuilder(classLoader, "buildertest.Hidden", "buildertest.Private")
    }

    /**
     * Requests a builder for each annotation of the package {@code buildertest} that is annotated with
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
            return "buildertest"
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
     * The aggregating variant of {@link RequestingVisitor}, for the package {@code aggregatingbuildertest}.
     */
    static class AggregatingRequestingVisitor extends RequestingVisitor {

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.AGGREGATING
        }

        @Override
        protected String requestingPackage() {
            return "aggregatingbuildertest"
        }
    }
}
