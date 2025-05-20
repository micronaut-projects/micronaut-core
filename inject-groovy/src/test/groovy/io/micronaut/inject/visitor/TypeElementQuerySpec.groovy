package io.micronaut.inject.visitor


import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

class TypeElementQuerySpec extends AbstractBeanDefinitionSpec {

    void 'test default'() {
        when:
            TypeElementQueryVisitor.ENABLED = true
            def definition = buildBeanDefinition('test.TEQTest1', '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Prototype;

@Prototype
class TEQTest1 {

    private String field1;
    private String field2;
    private String field3;
    private String field4;

    public TEQTest1(String field1) {
        this.field1 = field1;
    }

    public TEQTest1(String field1, String field2) {
        this.field1 = field1;
        this.field2 = field2;
    }

    @Executable
    public String myMethod1() {
        return null;
    }

    public String myMethod2() {
        return null;
    }

    public String myMethod3() {
        return null;
    }

}

''')
        then:
            definition
            TypeElementQueryVisitor.VISITED_CLASSES.size() == 1
            TypeElementQueryVisitor.VISITED_CONSTRUCTORS.size() == 2
            TypeElementQueryVisitor.VISITED_FIELDS.size() == 4
            TypeElementQueryVisitor.VISITED_ENUM_CONSTANTS.size() == 0
            TypeElementQueryVisitor.VISITED_METHODS.size() == 3

        cleanup:
            TypeElementQueryVisitor.cleanup()
    }

    void 'test default enum'() {
        when:
            TypeElementQueryVisitor.ENABLED = true
            def definition = buildBeanIntrospection('test.TEQTest2', '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;

@Introspected
enum TEQTest2 {

    A("AA"), B("BB");

    private String field1;
    private String field2;
    private String field3;
    private String field4;

    TEQTest2(String field1) {
        this.field1 = field1;
    }

    TEQTest2(String field1, String field2) {
        this.field1 = field1;
        this.field2 = field2;
    }

    @Executable
    public String myMethod1() {
        return null;
    }

    public String myMethod2() {
        return null;
    }

    public String myMethod3() {
        return null;
    }

}

''')
        then:
            definition
            TypeElementQueryVisitor.VISITED_CLASSES.size() == 1
            TypeElementQueryVisitor.VISITED_CONSTRUCTORS.size() == 2
            TypeElementQueryVisitor.VISITED_FIELDS.size() == 6 // + Min / Max fields
            TypeElementQueryVisitor.VISITED_ENUM_CONSTANTS.size() == 0
            TypeElementQueryVisitor.VISITED_METHODS.size() == 7 // + enum methods

        cleanup:
            TypeElementQueryVisitor.cleanup()
    }

    void 'test fields'() {
        when:
            TypeElementQueryVisitor.ENABLED = true
            TypeElementQueryVisitor.QUERY = TypeElementQueryVisitor.QUERY.excludeAll().includeFields()
            def definition = buildBeanDefinition('test.TEQTest1', '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Prototype;

@Prototype
class TEQTest1 {

    private String field1;
    private String field2;
    private String field3;
    private String field4;

    public TEQTest1(String field1) {
        this.field1 = field1;
    }

    public TEQTest1(String field1, String field2) {
        this.field1 = field1;
        this.field2 = field2;
    }

    @Executable
    public String myMethod1() {
        return null;
    }

    public String myMethod2() {
        return null;
    }

    public String myMethod3() {
        return null;
    }

}

''')
        then:
            definition
            TypeElementQueryVisitor.VISITED_CLASSES.size() == 1
            TypeElementQueryVisitor.VISITED_CONSTRUCTORS.size() == 0
            TypeElementQueryVisitor.VISITED_FIELDS.size() == 4
            TypeElementQueryVisitor.VISITED_ENUM_CONSTANTS.size() == 0
            TypeElementQueryVisitor.VISITED_METHODS.size() == 0

        cleanup:
            TypeElementQueryVisitor.cleanup()
    }

    void 'test methods'() {
        when:
            TypeElementQueryVisitor.ENABLED = true
            TypeElementQueryVisitor.QUERY = TypeElementQueryVisitor.QUERY.excludeAll().includeMethods()
            def definition = buildBeanDefinition('test.TEQTest1', '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Prototype;

@Prototype
class TEQTest1 {

    private String field1;
    private String field2;
    private String field3;
    private String field4;

    public TEQTest1(String field1) {
        this.field1 = field1;
    }

    public TEQTest1(String field1, String field2) {
        this.field1 = field1;
        this.field2 = field2;
    }

    @Executable
    public String myMethod1() {
        return null;
    }

    public String myMethod2() {
        return null;
    }

    public String myMethod3() {
        return null;
    }

}

''')
        then:
            definition
            TypeElementQueryVisitor.VISITED_CLASSES.size() == 1
            TypeElementQueryVisitor.VISITED_CONSTRUCTORS.size() == 0
            TypeElementQueryVisitor.VISITED_FIELDS.size() == 0
            TypeElementQueryVisitor.VISITED_ENUM_CONSTANTS.size() == 0
            TypeElementQueryVisitor.VISITED_METHODS.size() == 3

        cleanup:
            TypeElementQueryVisitor.cleanup()
    }

    void 'test constructors'() {
        when:
            TypeElementQueryVisitor.ENABLED = true
            TypeElementQueryVisitor.QUERY = TypeElementQueryVisitor.QUERY.excludeAll().includeConstructors()
            def definition = buildBeanDefinition('test.TEQTest1', '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Prototype;

@Prototype
class TEQTest1 {

    private String field1;
    private String field2;
    private String field3;
    private String field4;

    public TEQTest1(String field1) {
        this.field1 = field1;
    }

    public TEQTest1(String field1, String field2) {
        this.field1 = field1;
        this.field2 = field2;
    }

    @Executable
    public String myMethod1() {
        return null;
    }

    public String myMethod2() {
        return null;
    }

    public String myMethod3() {
        return null;
    }

}

''')
        then:
            definition
            TypeElementQueryVisitor.VISITED_CLASSES.size() == 1
            TypeElementQueryVisitor.VISITED_CONSTRUCTORS.size() == 2
            TypeElementQueryVisitor.VISITED_FIELDS.size() == 0
            TypeElementQueryVisitor.VISITED_ENUM_CONSTANTS.size() == 0
            TypeElementQueryVisitor.VISITED_METHODS.size() == 0

        cleanup:
            TypeElementQueryVisitor.cleanup()
    }

    void 'test fields and methods'() {
        when:
            TypeElementQueryVisitor.ENABLED = true
            TypeElementQueryVisitor.QUERY = TypeElementQueryVisitor.QUERY.excludeAll().includeMethods().includeFields()
            def definition = buildBeanDefinition('test.TEQTest1', '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Prototype;

@Prototype
class TEQTest1 {

    private String field1;
    private String field2;
    private String field3;
    private String field4;

    public TEQTest1(String field1) {
        this.field1 = field1;
    }

    public TEQTest1(String field1, String field2) {
        this.field1 = field1;
        this.field2 = field2;
    }

    @Executable
    public String myMethod1() {
        return null;
    }

    public String myMethod2() {
        return null;
    }

    public String myMethod3() {
        return null;
    }

}

''')
        then:
            definition
            TypeElementQueryVisitor.VISITED_CLASSES.size() == 1
            TypeElementQueryVisitor.VISITED_CONSTRUCTORS.size() == 0
            TypeElementQueryVisitor.VISITED_FIELDS.size() == 4
            TypeElementQueryVisitor.VISITED_ENUM_CONSTANTS.size() == 0
            TypeElementQueryVisitor.VISITED_METHODS.size() == 3

        cleanup:
            TypeElementQueryVisitor.cleanup()
    }


}
