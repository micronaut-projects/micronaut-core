package io.micronaut.inject.visitor

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.EnumConstantElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement

class TypeElementQueryVisitor implements TypeElementVisitor<Object, Object> {

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
