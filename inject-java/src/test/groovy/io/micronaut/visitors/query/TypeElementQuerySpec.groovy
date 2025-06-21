package io.micronaut.visitors.query

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.EnumConstantElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.visitor.TypeElementQuery
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class TypeElementQuerySpec extends AbstractTypeElementSpec {

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
            TypeElementQueryVisitor.VISITED_FIELDS.size() == 4
            TypeElementQueryVisitor.VISITED_ENUM_CONSTANTS.size() == 0
            TypeElementQueryVisitor.VISITED_METHODS.size() == 5

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

    static class TypeElementQueryVisitor implements TypeElementVisitor<Object, Object> {

        static boolean ENABLED = false
        static List<ClassElement> VISITED_CLASSES = new ArrayList<>()
        static List<MethodElement> VISITED_METHODS = new ArrayList<>()
        static List<ConstructorElement> VISITED_CONSTRUCTORS = new ArrayList<>()
        static List<FieldElement> VISITED_FIELDS = new ArrayList<>()
        static List<EnumConstantElement> VISITED_ENUM_CONSTANTS = new ArrayList<>()
        static TypeElementQuery QUERY = TypeElementQuery.DEFAULT

        static void cleanup() {
            VISITED_CLASSES.clear()
            VISITED_METHODS.clear()
            VISITED_CONSTRUCTORS.clear()
            VISITED_FIELDS.clear()
            VISITED_ENUM_CONSTANTS.clear()
            QUERY = TypeElementQuery.DEFAULT
            ENABLED = false
        }

        @Override
        TypeElementQuery query() {
            return QUERY
        }

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (ENABLED) {
                VISITED_CLASSES.add(element)
            }
        }

        @Override
        void visitConstructor(ConstructorElement element, VisitorContext context) {
            if (ENABLED) {
                VISITED_CONSTRUCTORS.add(element)
            }
        }

        @Override
        void visitEnumConstant(EnumConstantElement element, VisitorContext context) {
            if (ENABLED) {
                VISITED_ENUM_CONSTANTS.add(element)
            }
        }

        @Override
        void visitField(FieldElement element, VisitorContext context) {
            if (ENABLED) {
                VISITED_FIELDS.add(element)
            }
        }

        @Override
        void visitMethod(MethodElement element, VisitorContext context) {
            if (ENABLED) {
                VISITED_METHODS.add(element)
            }
        }

    }

}
