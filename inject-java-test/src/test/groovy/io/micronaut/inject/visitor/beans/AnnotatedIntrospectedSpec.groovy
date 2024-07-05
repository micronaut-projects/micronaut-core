package io.micronaut.inject.visitor.beans

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor

import javax.annotation.processing.SupportedAnnotationTypes

class AnnotatedIntrospectedSpec extends AbstractTypeElementSpec {

    void "test make introspected"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.*;
import java.util.*;

@io.micronaut.inject.visitor.beans.MakeIntrospected
class Test extends Parent {
    private String name;
    public int foobar;
    public String getName() {
        return this.name;
    }
    public Test setName(String n) {
        this.name = n;
        return this;
    }
}

class Parent {
    public String bar;
}
''')

        expect:
        introspection != null
        introspection.getPropertyNames() as Set == ["name", "bar", "foobar"] as Set
    }
    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {
            @Override
            protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                return new MyTypeElementVisitorProcessor()
            }
        }
    }

    @SupportedAnnotationTypes("*")
    static class MyTypeElementVisitorProcessor extends TypeElementVisitorProcessor {

        @NonNull
        @Override
        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
            return [new MakeIntrospectedVisitor(), new IntrospectedTypeElementVisitor()]
        }
    }
}
