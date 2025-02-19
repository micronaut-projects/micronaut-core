/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.utils.NativeElementsHelper;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The Java native element helper.
 *
 * @author Denis Stepanov
 * @since 4.3.0
 */
@Internal
public final class JavaNativeElementsHelper extends NativeElementsHelper<TypeElement, ExecutableElement> {

    private final Elements elementUtils;
    private final Types typeUtils;

    public JavaNativeElementsHelper(Elements elementUtils, Types typeUtils) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
    }

    public Collection<ExecutableElement> findOverriddenMethods(ExecutableElement methodElement) {
        return findOverriddenMethods((TypeElement) methodElement.getEnclosingElement(), methodElement);
    }

    @Override
    protected boolean overrides(ExecutableElement m1, ExecutableElement m2, TypeElement typeElement) {
        return elementUtils.overrides(m1, m2, typeElement);
    }

    @NonNull
    @Override
    protected String getMethodName(ExecutableElement element) {
        return element.getSimpleName().toString();
    }

    @Override
    protected TypeElement getSuperClass(TypeElement classNode) {
        TypeMirror superclass = classNode.getSuperclass();
        if (superclass.getKind() == TypeKind.NONE) {
            return null;
        }
        DeclaredType kind = (DeclaredType) superclass;
        return (TypeElement) kind.asElement();
    }

    @NonNull
    @Override
    protected Collection<TypeElement> getInterfaces(TypeElement classNode) {
        List<? extends TypeMirror> interfacesMirrors = classNode.getInterfaces();
        var interfaces = new ArrayList<TypeElement>(interfacesMirrors.size());
        for (TypeMirror anInterface : interfacesMirrors) {
            final Element e = typeUtils.asElement(anInterface);
            if (e instanceof TypeElement te) {
                interfaces.add(te);
            }
        }
        return interfaces;
    }

    @NonNull
    @Override
    protected List<ExecutableElement> getMethods(TypeElement classNode) {
        return ElementFilter.methodsIn(classNode.getEnclosedElements());
    }

    @Override
    protected boolean excludeClass(TypeElement classNode) {
        return classNode.getQualifiedName().toString().equals(Object.class.getName())
            || classNode.getQualifiedName().toString().equals(Enum.class.getName());
    }

    @Override
    protected boolean isInterface(TypeElement classNode) {
        return classNode.getKind() == ElementKind.INTERFACE;
    }

}
