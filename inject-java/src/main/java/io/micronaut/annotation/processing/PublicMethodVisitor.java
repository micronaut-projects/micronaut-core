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
import io.micronaut.inject.visitor.VisitorContext;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.util.Types;
import java.util.Set;

/**
 * An adapter that implements all methods of the {@link javax.lang.model.type.TypeVisitor}
 * interface subclasses can selectively override.
 *
 * @param <R> The return type of the visitor's method
 * @param <P> The type of the additional parameter to the visitor's methods.
 * @author graemerocher
 * @see javax.lang.model.util.AbstractTypeVisitor8
 * @since 1.0
 */
public abstract class PublicMethodVisitor<R, P> extends SuperclassAwareTypeVisitor<R, P> {

    /**
     * Default constructor.
     *
     * @param visitorContext The visitor context
     */
    protected PublicMethodVisitor(JavaVisitorContext visitorContext) {
        super(visitorContext);
    }

    /**
     * Only accepts public non file or static methods.
     *
     * @param element The {@link Element}
     * @return If the element is acceptable
     */
    @Override
    protected boolean isAcceptable(Element element) {
        if (element.getKind() == ElementKind.METHOD) {
            Set<Modifier> modifiers = element.getModifiers();
            return modifiers.contains(Modifier.PUBLIC) && !modifiers.contains(Modifier.FINAL) && !modifiers.contains(Modifier.STATIC);
        } else {
            return false;
        }
    }
}
