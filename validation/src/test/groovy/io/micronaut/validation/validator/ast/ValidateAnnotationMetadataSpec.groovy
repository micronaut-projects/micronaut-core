package io.micronaut.validation.validator.ast

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor

import javax.annotation.processing.SupportedAnnotationTypes

class ValidateAnnotationMetadataSpec extends AbstractTypeElementSpec {

    void "test validate annotation values on type with invalid constant"() {
        when:
        buildBeanIntrospection('test.Test', '''
package test;
@io.micronaut.validation.validator.ast.SomeAnn(Test.VAL) // blank not allowed
@io.micronaut.core.annotation.Introspected
class Test {
    public static final String VAL = "";
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message == '''test/Test.java:5: error: @SomeAnn.value: must not be blank
class Test {
^'''

    }

    void "test validate annotation values on type"() {
        when:
        buildBeanIntrospection('test.Test', '''
package test;
@io.micronaut.validation.validator.ast.SomeAnn("") // blank not allowed
@io.micronaut.core.annotation.Introspected
class Test {
    private String name;
    public String getName() {
        return this.name;
    }
    public Test setName(String n) {
        this.name = n;
        return this;
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message == '''test/Test.java:5: error: @SomeAnn.value: must not be blank
class Test {
^'''

    }

    void "test validate annotation values on field"() {
        when:
        buildBeanIntrospection('test.Test', '''
package test;

@io.micronaut.core.annotation.Introspected
class Test {
    @io.micronaut.validation.validator.ast.SomeAnn("") // blank not allowed
    private String name;
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message == '''test/Test.java:7: error: @SomeAnn.value: must not be blank
    private String name;
                   ^'''

    }

    void "test validate annotation values on method"() {
        when:
        buildBeanIntrospection('test.Test', '''
package test;

@io.micronaut.core.annotation.Introspected
class Test {
    @io.micronaut.validation.validator.ast.SomeAnn("") // blank not allowed
    String getName() {
        return null;
    };
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message == '''test/Test.java:7: error: @SomeAnn.value: must not be blank
    String getName() {
           ^'''

    }

    void "test validate annotation values on parameter"() {
        when:
        buildBeanDefinition('test.Test', '''
package test;

import io.micronaut.validation.validator.ast.*;

@javax.inject.Singleton
class Test {

    @io.micronaut.context.annotation.Executable
    String getName(@SomeAnn("") String n) {
        return null;
    }
}
''')

        then:
        def e = thrown(RuntimeException)
        e.message == '''test/Test.java:10: error: @SomeAnn.value: must not be blank
    String getName(@SomeAnn("") String n) {
                                       ^'''

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
        @Override
        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
            return [new IntrospectedTypeElementVisitor()]
        }
    }
}
