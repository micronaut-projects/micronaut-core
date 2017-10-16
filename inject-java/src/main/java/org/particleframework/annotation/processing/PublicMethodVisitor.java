/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.annotation.processing;

import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.AbstractTypeVisitor8;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * An adapter that implements all methods of the {@link TypeVisitor} interface. Subclasses can selectively override
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class PublicMethodVisitor<R,P> extends AbstractTypeVisitor8<R,P> {
    private final Set<String> processed = new HashSet<>();
    @Override
    public R visitIntersection(IntersectionType t, P p) {
        return null;
    }

    @Override
    public R visitPrimitive(PrimitiveType t, P p) {
        return null;
    }

    @Override
    public R visitNull(NullType t, P p) {
        return null;
    }

    @Override
    public R visitArray(ArrayType t, P p) {
        return null;
    }

    @Override
    public R visitDeclared(DeclaredType type, P p) {
        Element element = type.asElement();

        while((element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.INTERFACE) && !element.toString().equals(Object.class.getName())) {
            TypeElement typeElement = (TypeElement) element;
            List<? extends Element> enclosedElements = typeElement.getEnclosedElements();
            for (Element enclosedElement : enclosedElements) {
                if(enclosedElement.getKind() == ElementKind.METHOD) {
                    boolean isAcceptable = isAcceptable(enclosedElement);
                    ExecutableElement theMethod = (ExecutableElement) enclosedElement;
                    if(isAcceptable) {
                        String qualifiedName = theMethod.toString();
                        // if the method has already been processed then it is overridden so ignore
                        if(!processed.contains(qualifiedName)) {
                            processed.add(qualifiedName);
                            accept(theMethod, p);
                        }
                    }

                }
            }
            List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
            for (TypeMirror anInterface : interfaces) {
                if(anInterface instanceof DeclaredType) {

                    DeclaredType interfaceType = (DeclaredType) anInterface;
                    visitDeclared(interfaceType, p);
                }
            }
            TypeMirror superMirror = typeElement.getSuperclass();
            if(superMirror instanceof DeclaredType) {
                element = ((DeclaredType)superMirror).asElement();
            }
            else {
                break;
            }
        }

        return null;
    }

    protected boolean isAcceptable(Element enclosedElement) {
        Set<Modifier> modifiers = enclosedElement.getModifiers();
        return modifiers.contains(Modifier.PUBLIC) && !modifiers.contains(Modifier.FINAL);
    }

    protected abstract void accept(ExecutableElement method, P p);

    @Override
    public R visitError(ErrorType t, P p) {
        return null;
    }

    @Override
    public R visitTypeVariable(TypeVariable t, P p) {
        return null;
    }

    @Override
    public R visitWildcard(WildcardType t, P p) {
        return null;
    }

    @Override
    public R visitExecutable(ExecutableType t, P p) {
        return null;
    }

    @Override
    public R visitNoType(NoType t, P p) {
        return null;
    }

    @Override
    public R visitUnion(UnionType t, P p) {
        return null;
    }
}
