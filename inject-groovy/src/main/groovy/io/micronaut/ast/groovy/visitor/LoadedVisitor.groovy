/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.ast.groovy.visitor

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.Internal
import io.micronaut.core.order.Ordered
import io.micronaut.inject.visitor.TypeElementVisitor
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.SourceUnit
/**
 * Used to store a reference to an underlying {@link TypeElementVisitor} and
 * optionally invoke the visit methods on the visitor if it matches the
 * element being visited by the AST transformation.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Internal
@CompileStatic
class LoadedVisitor implements Ordered {

    private final SourceUnit sourceUnit
    private final TypeElementVisitor visitor
    private final String classAnnotation
    private final String elementAnnotation
    private final CompilationUnit compilationUnit

    private static final String OBJECT_CLASS = Object.class.getName()

    LoadedVisitor(SourceUnit source, CompilationUnit compilationUnit, TypeElementVisitor visitor) {
        this.compilationUnit = compilationUnit
        this.sourceUnit = source
        this.visitor = visitor
        ClassNode classNode = ClassHelper.make(visitor.getClass())
        ClassNode definition = classNode.getAllInterfaces().find {
            it.name == TypeElementVisitor.class.name
        }
        GenericsType[] generics = definition.getGenericsTypes()
        if (generics) {
            String typeName = generics[0].type.name
            if (typeName == OBJECT_CLASS) {
                classAnnotation = visitor.getClassType()
            } else {
                classAnnotation = typeName
            }
            String elementName = generics[1].type.name
            if (elementName == OBJECT_CLASS) {
                elementAnnotation = visitor.getElementType()
            } else {
                elementAnnotation = elementName
            }
        } else {
            classAnnotation = ClassHelper.OBJECT
            elementAnnotation = ClassHelper.OBJECT
        }
    }

    TypeElementVisitor getVisitor() {
        visitor
    }

    @Override
    int getOrder() {
        return getVisitor().getOrder()
    }

    @Override
    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        LoadedVisitor that = (LoadedVisitor) o

        if (visitor.getClass() != that.getClass() ) return false

        return true
    }

    @Override
    int hashCode() {
        return visitor.getClass().hashCode()
    }

    @Override
    String toString() {
        visitor.toString()
    }

    /**
     * @param annotationMetadata The annotation data
     * @return True if the class should be visited
     */
    boolean matchesClass(AnnotationMetadata annotationMetadata) {
        if (classAnnotation == ClassHelper.OBJECT) {
            return true
        }
        return annotationMetadata.hasStereotype(classAnnotation)
    }

    /**
     * @param annotationMetadata The annotation data
     * @return True if the element should be visited
     */
    boolean matchesElement(AnnotationMetadata annotationMetadata) {
        if (elementAnnotation == ClassHelper.OBJECT) {
            return true
        }
        return annotationMetadata.hasStereotype(elementAnnotation)
    }

    void start(GroovyVisitorContext visitorContext) {
        visitor.start(visitorContext)
    }

    void finish(GroovyVisitorContext visitorContext) {
        visitor.finish(visitorContext)
    }
}
