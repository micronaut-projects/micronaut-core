package io.micronaut

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.inject.visitor.ClassElement
import io.micronaut.inject.visitor.FieldElement
import io.micronaut.inject.visitor.MethodElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

class MyVisitor implements TypeElementVisitor<Controller, Get> {


    @Override
    void visitClass(ClassElement element, VisitorContext context) {

    }

    @Override
    void visitMethod(MethodElement element, VisitorContext context) {

    }

    @Override
    void visitField(FieldElement element, VisitorContext context) {

    }
}
