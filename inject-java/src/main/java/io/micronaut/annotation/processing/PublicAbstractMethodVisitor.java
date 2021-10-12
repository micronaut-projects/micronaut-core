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

import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility visitor that only visits public abstract methods that have not been implemented by the given type.
 *
 * @param <R> The return type of the visitor's method
 * @param <P> The type of the additional parameter to the visitor's methods.
 * @author graemerocher
 * @see javax.lang.model.util.AbstractTypeVisitor8
 * @since 1.0
 */
public abstract class PublicAbstractMethodVisitor<R, P> extends PublicMethodVisitor<R, P> {

    private final TypeElement classElement;
    private final ModelUtils modelUtils;
    private final Elements elementUtils;

    private Map<String, List<ExecutableElement>> declaredMethods = new HashMap<>();

    /**
     * @param classElement The class element
     * @param visitorContext The visitor context
     */
    PublicAbstractMethodVisitor(TypeElement classElement,
                                JavaVisitorContext visitorContext) {
        super(visitorContext);
        this.classElement = classElement;
        this.modelUtils = visitorContext.getModelUtils();
        this.elementUtils = visitorContext.getElements();
    }

    @Override
    protected boolean isAcceptable(Element element) {
        if (element.getKind() == ElementKind.METHOD) {
            ExecutableElement executableElement = (ExecutableElement) element;
            Set<Modifier> modifiers = executableElement.getModifiers();
            String methodName = executableElement.getSimpleName().toString();
            boolean acceptable = isAcceptableMethod(executableElement) && !modifiers.contains(Modifier.FINAL) && !modifiers.contains(Modifier.STATIC);
            boolean isDeclared = executableElement.getEnclosingElement().equals(classElement);
            if (acceptable && !isDeclared && declaredMethods.containsKey(methodName)) {
                // check method is not overridden already
                for (ExecutableElement ex : declaredMethods.get(methodName)) {
                    if (elementUtils.overrides(ex, executableElement, classElement)) {
                        return false;
                    }
                }
            } else if (!acceptable && !modelUtils.isStatic(executableElement)) {
                List<ExecutableElement> declaredMethodList = declaredMethods.computeIfAbsent(methodName, s -> new ArrayList<>());
                declaredMethodList.add(executableElement);
            }
            return acceptable;
        } else {
            return false;
        }
    }

    /**
     * Return whether the given executable element is acceptable. By default just checks if the method is abstract.
     * @param executableElement The method
     * @return True if it is
     */
    protected boolean isAcceptableMethod(ExecutableElement executableElement) {
        return modelUtils.isAbstract(executableElement);
    }
}
