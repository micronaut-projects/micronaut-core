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
import java.util.*;
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
        return visitDeclared(type, p, true);
    }

    private R visitDeclared(DeclaredType type, P p, boolean visitInterfaces) {
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
            if (visitInterfaces) {
                List<TypeMirror> interfaces = new ArrayList<>();
                for (TypeMirror anInterface : typeElement.getInterfaces()) {
                    if (anInterface instanceof DeclaredType) {
                        DeclaredType interfaceType = (DeclaredType) anInterface;
                        interfaces.add(anInterface);
                        interfaces.addAll(getInterfaces(interfaceType));
                    }
                }
                interfaces.stream()
                        .distinct()
                        .sorted((o1, o2) -> {
                            if (types.isSubtype(o1, o2)) {
                                return -1;
                            } else {
                                return 0;
                            }
                        })
                        .filter(DeclaredType.class::isInstance)
                        .map(DeclaredType.class::cast)
                        .forEach((dt) -> visitDeclared(dt, p, false));
            }
        }

        return null;
    }

    private List<TypeMirror> getInterfaces(DeclaredType declaredType) {
        Element interfaceElement = declaredType.asElement();
        List<TypeMirror> interfaces = new ArrayList<>();
        if (interfaceElement instanceof TypeElement) {
            TypeElement interfaceTypeElement = (TypeElement) interfaceElement;
            for (TypeMirror anInterface : interfaceTypeElement.getInterfaces()) {
                if (anInterface instanceof DeclaredType) {
                    DeclaredType interfaceType = (DeclaredType) anInterface;
                    interfaces.add(interfaceType);
                    interfaces.addAll(getInterfaces(interfaceType));
                }
            }
        }
        return interfaces;
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
        String qualifiedName = enclosedElement.getSimpleName().toString();
        qualifiedName += "(" + enclosedElement.getParameters().stream().map(variableElement -> types.erasure(variableElement.asType()).toString()).collect(Collectors.joining(",")) + ")";

        TypeMirror returnTypeMirror = enclosedElement.getReturnType();
        String returnType = null;

        if (returnTypeMirror.getKind() == TypeKind.TYPEVAR) {
            Map<String, TypeMirror> generics = genericUtils.buildGenericTypeArgumentInfo(type)
                    .get(typeElement.getQualifiedName().toString());
            if (generics != null) {
                String key = returnTypeMirror.toString();
                if (generics.containsKey(key)) {
                    returnType = generics.get(key).toString();
                }
            }
        }

        if (returnType == null) {
            returnType = modelUtils.resolveTypeName(returnTypeMirror);
        }

        qualifiedName = returnType + "." + qualifiedName;
        return qualifiedName;
    }
}
