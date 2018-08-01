/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.annotation.processing;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.AbstractTypeVisitor8;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An adapter that implements all methods of the {@link javax.lang.model.type.TypeVisitor} interface. Subclasses can
 * selectively override.
 *
 * @param <R> The return type of the visitor's method
 * @param <P> The type of the additional parameter to the visitor's methods.
 * @author graemerocher
 * @see javax.lang.model.util.AbstractTypeVisitor8
 * @since 1.0
 */
public abstract class PublicMethodVisitor<R, P> extends AbstractTypeVisitor8<R, P> {
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

        while ((element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.INTERFACE) && !element.toString().equals(Object.class.getName())) {
            TypeElement typeElement = (TypeElement) element;
            List<? extends Element> enclosedElements = typeElement.getEnclosedElements();
            for (Element enclosedElement : enclosedElements) {
                if (enclosedElement.getKind() == ElementKind.METHOD) {
                    boolean isAcceptable = isAcceptable((ExecutableElement) enclosedElement);
                    ExecutableElement theMethod = (ExecutableElement) enclosedElement;
                    if (isAcceptable) {
                        String qualifiedName = theMethod.toString();
                        // if the method has already been processed then it is overridden so ignore
                        if (!processed.contains(qualifiedName)) {
                            processed.add(qualifiedName);
                            accept(type, theMethod, p);
                        }
                    }

                }
            }
            List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
            for (TypeMirror anInterface : interfaces) {
                if (anInterface instanceof DeclaredType) {

                    DeclaredType interfaceType = (DeclaredType) anInterface;
                    visitDeclared(interfaceType, p);
                }
            }
            TypeMirror superMirror = typeElement.getSuperclass();
            if (superMirror instanceof DeclaredType) {
                element = ((DeclaredType) superMirror).asElement();
            } else {
                break;
            }
        }

        return null;
    }

    /**
     * @param executableElement The {@link ExecutableElement}
     * @return Whether the element is public and final
     */
    protected boolean isAcceptable(ExecutableElement executableElement) {
        Set<Modifier> modifiers = executableElement.getModifiers();
        return modifiers.contains(Modifier.PUBLIC) && !modifiers.contains(Modifier.FINAL) && !modifiers.contains(Modifier.STATIC);
    }

    /**
     * @param type   The {@link DeclaredType}
     * @param method The {@link ExecutableElement}
     * @param p      The additional type
     */
    protected abstract void accept(DeclaredType type, ExecutableElement method, P p);

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
