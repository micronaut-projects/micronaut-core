package io.micronaut.inject.visitor.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.core.beans.BeanIntrospection
import org.jspecify.annotations.NonNull

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

/**
 * A type whose static method returns a type that another annotation processor
 * generates in the same compilation cannot be fully read in the round it is
 * first seen, so the visitor postpones it to the next round. Its introspection
 * must still be written once the generated type exists.
 */
class PostponedTypeIntrospectionSpec extends AbstractTypeElementSpec {

    void "a record postponed because its static method returns a type generated in the same compilation is introspected"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection('test.Widget', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
@GenerateBuilder
public record Widget(String name) {
    public static WidgetBuilder named(String name) {
        return new WidgetBuilder().name(name);
    }
}

@interface GenerateBuilder {}
''')

        then:
        introspection.beanType.name == 'test.Widget'
        introspection.instantiate('widget').name() == 'widget'
    }

    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {
            @Override
            protected @NonNull List<Processor> getAnnotationProcessors() {
                return [new BuilderGeneratingProcessor()] + super.getAnnotationProcessors()
            }
        }
    }

    /**
     * Generates {@code <Name>Builder} for each type annotated {@code test.GenerateBuilder},
     * as builder generators such as RecordBuilder do.
     */
    static class BuilderGeneratingProcessor extends AbstractProcessor {

        @Override
        Set<String> getSupportedAnnotationTypes() {
            return ['test.GenerateBuilder'] as Set
        }

        @Override
        SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported()
        }

        @Override
        boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            for (TypeElement annotation : annotations) {
                for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                    String name = element.simpleName.toString()
                    processingEnv.filer.createSourceFile("test.${name}Builder", element).openWriter().withWriter { writer ->
                        writer << """package test;

public class ${name}Builder {
    private String name;

    public ${name}Builder name(String name) {
        this.name = name;
        return this;
    }

    public ${name} build() {
        return new ${name}(name);
    }
}
"""
                    }
                }
            }
            return false
        }
    }
}
