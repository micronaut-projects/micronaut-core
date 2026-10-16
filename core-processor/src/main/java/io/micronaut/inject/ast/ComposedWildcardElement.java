/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.ast;

import io.micronaut.core.annotation.Internal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A {@link WildcardElement} composed of the class elements of its bounds, see {@link WildcardElement#of(List, List)}.
 * Like the wildcards of the compilers, it acts as its first upper bound when used as a {@link ClassElement}.
 *
 * @param upperBounds The upper bounds, never empty
 * @param lowerBounds The lower bounds
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record ComposedWildcardElement(List<ClassElement> upperBounds,
                               List<ClassElement> lowerBounds) implements WildcardElement {

    private ClassElement upperBound() {
        return upperBounds.get(0);
    }

    @Override
    public List<? extends ClassElement> getUpperBounds() {
        return upperBounds;
    }

    @Override
    public List<? extends ClassElement> getLowerBounds() {
        return lowerBounds;
    }

    @Override
    public String getName() {
        return upperBound().getName();
    }

    @Override
    public boolean isAssignable(String type) {
        return upperBound().isAssignable(type);
    }

    @Override
    public boolean isAssignable(ClassElement type) {
        return upperBound().isAssignable(type);
    }

    @Override
    public boolean isAssignable(Class<?> type) {
        return upperBound().isAssignable(type);
    }

    @Override
    public boolean isInterface() {
        return upperBound().isInterface();
    }

    @Override
    public Optional<ClassElement> getSuperType() {
        return upperBound().getSuperType();
    }

    @Override
    public Collection<ClassElement> getInterfaces() {
        return upperBound().getInterfaces();
    }

    @Override
    public Map<String, ClassElement> getTypeArguments() {
        return upperBound().getTypeArguments();
    }

    @Override
    public Map<String, Map<String, ClassElement>> getAllTypeArguments() {
        return upperBound().getAllTypeArguments();
    }

    @Override
    public List<? extends ClassElement> getBoundGenericTypes() {
        return upperBound().getBoundGenericTypes();
    }

    @Override
    public boolean isProtected() {
        return upperBound().isProtected();
    }

    @Override
    public boolean isPublic() {
        return upperBound().isPublic();
    }

    @Override
    public Object getNativeType() {
        return upperBound().getNativeType();
    }

    @Override
    public Object getGenericNativeType() {
        return this;
    }

    @Override
    public ClassElement toArray() {
        throw new UnsupportedOperationException("A wildcard cannot be an array component");
    }

    @Override
    public ClassElement fromArray() {
        throw new UnsupportedOperationException("A wildcard is not an array");
    }

    @Override
    public String toString() {
        if (!lowerBounds.isEmpty()) {
            return "? super " + lowerBounds.stream().map(ClassElement::getName).collect(Collectors.joining(" & "));
        }
        if (!isBounded()) {
            return "?";
        }
        return "? extends " + upperBounds.stream().map(ClassElement::getName).collect(Collectors.joining(" & "));
    }
}
