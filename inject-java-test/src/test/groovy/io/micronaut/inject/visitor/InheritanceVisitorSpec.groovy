package io.micronaut.inject.visitor

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor

import javax.annotation.processing.SupportedAnnotationTypes

class InheritanceVisitorSpec extends AbstractTypeElementSpec {

    AllClassesVisitor allClassesVisitor = new AllClassesVisitor()

    def setup() {
        allClassesVisitor.reset()
    }

    void "test write bean introspection with builder style properties"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import javax.validation.constraints.*;
import java.util.*;

@Introspected
class Test extends Parent {
    private String name;
    public String getName() {
        return this.name;
    }
    public Test setName(String n) {
        this.name = n;
        return this;
    }
}

class Parent {
    private String bar;
    public String getBar() {
        return this.bar;
    }
    public void setBar(String n) {
        this.bar = n;
    }
}
''')

        expect:
        introspection != null
        def properties = allClassesVisitor.visitedClassElements[0].beanProperties
        properties.size() == 2
        def nameProp = properties.find { it.name == "name"}
        nameProp.name == 'name'
        nameProp.declaringType.name == 'test.Test'
        def barProp = properties.find { it.name == "bar"}
        barProp.name == 'bar'
        barProp.declaringType.name == 'test.Parent'
        // result is 6 because the Parent is visited through Test and by itself
        allClassesVisitor.visitedMethodElements.size() == 6
    }
    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {
            @Override
            protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                return new MyTypeElementVisitorProcessor(allClassesVisitor)
            }
        }
    }

    @SupportedAnnotationTypes("*")
    static class MyTypeElementVisitorProcessor extends TypeElementVisitorProcessor {
        final AllClassesVisitor allClassesVisitor

        MyTypeElementVisitorProcessor(AllClassesVisitor allClassesVisitor) {
            this.allClassesVisitor = allClassesVisitor
        }

        @Override
        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
            return [new IntrospectedTypeElementVisitor(), allClassesVisitor]
        }
    }
}
