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
package io.micronaut.annotation.processing;

import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.inject.processing.JavaModelUtils;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import javax.lang.model.util.AbstractTypeVisitor8;
import javax.lang.model.util.Types;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Processes the type and its super classes.
 *
 * @param <R>
 * @param <P>
 */
public abstract class SuperclassAwareTypeVisitor<R, P> extends AbstractTypeVisitor8<R, P> {
    private final Set<String> processed = new HashSet<>();

    private final Types types;
    private final GenericUtils genericUtils;
    private final ModelUtils modelUtils;

    /**
     * Default constructor.
     *
     * @param visitorContext The visitor context
     */
    protected SuperclassAwareTypeVisitor(JavaVisitorContext visitorContext) {
        this.types = visitorContext.getTypes();
        this.genericUtils = visitorContext.getGenericUtils();
        this.modelUtils = visitorContext.getModelUtils();
    }

    @Override
    public R visitDeclared(DeclaredType type, P p) {
       final Element element = type.asElement();

        if ((JavaModelUtils.isClassOrInterface(element) || JavaModelUtils.isEnum(element)) &&
                !element.toString().equals(Object.class.getName()) &&
                !element.toString().equals(Enum.class.getName())) {
            TypeElement typeElement = (TypeElement) element;
            List<? extends Element> enclosedElements = typeElement.getEnclosedElements();
            for (Element enclosedElement : enclosedElements) {
                boolean isAcceptable = isAcceptable(enclosedElement);
                if (isAcceptable) {
                    String qualifiedName;
                    if (enclosedElement instanceof ExecutableElement) {
                        qualifiedName = buildQualifiedName(type, typeElement, (ExecutableElement) enclosedElement);
                    } else {
                        qualifiedName = types.erasure(enclosedElement.asType()).toString() + "." + enclosedElement.getSimpleName().toString();
                    }
                    // if the method has already been processed then it is overridden so ignore
                    if (!processed.contains(qualifiedName)) {
                        processed.add(qualifiedName);
                        accept(type, enclosedElement, p);
                    }
                } else if (enclosedElement instanceof ExecutableElement) {
                    ExecutableElement ee = (ExecutableElement) enclosedElement;
                    Set<Modifier> modifiers = ee.getModifiers();
                    if (!modifiers.contains(Modifier.PRIVATE) && !modifiers.contains(Modifier.STATIC)) {
                        String qualifiedName = buildQualifiedName(type, typeElement, ee);
                        // add to processed so that if a super method is visited that this method
                        // overrides it is not processed again
                        processed.add(qualifiedName);
                    }
                }
            }

            TypeMirror superMirror = typeElement.getSuperclass();
            if (superMirror instanceof DeclaredType) {
                visitDeclared((DeclaredType) superMirror, p);
            }
            List<? extends TypeMirror> interfaces = typeElement.getInterfaces();
            for (TypeMirror anInterface : interfaces) {
                if (anInterface instanceof DeclaredType) {

                    DeclaredType interfaceType = (DeclaredType) anInterface;
                    visitDeclared(interfaceType, p);
                }
            }
        }

        return null;
    }

    /**
     * @param element The {@link Element}
     * @return Whether the element is public and final
     */
    protected abstract boolean isAcceptable(Element element);

    /**
     * @param type    The {@link DeclaredType}
     * @param element The {@link Element}
     * @param p       The additional type
     */
    protected abstract void accept(DeclaredType type, Element element, P p);

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

    private String buildQualifiedName(DeclaredType type, TypeElement typeElement, ExecutableElement enclosedElement) {
        ExecutableElement ee = enclosedElement;
        String qualifiedName = ee.getSimpleName().toString();
        qualifiedName += "(" + ee.getParameters().stream().map(variableElement -> types.erasure(variableElement.asType()).toString()).collect(Collectors.joining(",")) + ")";

        TypeMirror returnTypeMirror = ee.getReturnType();
        String returnType = null;

        if (returnTypeMirror.getKind() == TypeKind.TYPEVAR) {
            Map<String, Object> generics = genericUtils.buildGenericTypeArgumentInfo(type)
                    .get(typeElement.getQualifiedName().toString());
            if (generics != null && generics.containsKey(returnTypeMirror.toString())) {
                returnType = generics.get(returnTypeMirror.toString()).toString();
            }
        }

        if (returnType == null) {
            returnType = modelUtils.resolveTypeName(returnTypeMirror);
        }

        qualifiedName = returnType + "." + qualifiedName;
        return qualifiedName;
    }
}
