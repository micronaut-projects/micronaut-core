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
package io.micronaut.inject.writer;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.Element;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Default implementation of the {@link OriginatingElements} interface.
 *
 * @author graemerocher
 * @since 2.1.1
 */
@Internal
final class DefaultOriginatingElements implements OriginatingElements {
    private final Map<String, Element> originatingElements;

    /**
     * Default constructor.
     * @param originatingElements The elements
     */
    DefaultOriginatingElements(Element... originatingElements) {
        this.originatingElements = new LinkedHashMap<>(originatingElements.length);
        for (Element originatingElement : originatingElements) {
            if (originatingElement != null) {
                this.originatingElements.put(originatingElement.getName(), originatingElement);
            }
        }
    }

    @Override
    public void addOriginatingElement(@NotNull Element element) {
        Objects.requireNonNull(element, "Element cannot be null");
        this.originatingElements.put(
                element.getName(),
                element
        );
    }

    @NotNull
    @Override
    public Element[] getOriginatingElements() {
        return this.originatingElements.values().toArray(Element.EMPTY_ELEMENT_ARRAY);
    }
}
