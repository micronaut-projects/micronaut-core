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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Utility class for testing originating element generation. Not for public use.
 *
 * @author graemerocher
 * @since 2.1.1
 */
@Internal
public final class StaticOriginatingElements implements OriginatingElements {
    public static final StaticOriginatingElements INSTANCE = new StaticOriginatingElements();
    private final Map<String, Element> originatingElements = new LinkedHashMap<>(5);

    private StaticOriginatingElements() {
    }

    @Override
    public void addOriginatingElement(@NotNull Element element) {
        Objects.requireNonNull(element, "Element cannot be null");
        originatingElements.put(
                element.getName(),
                element
        );
    }

    @NotNull
    @Override
    public Element[] getOriginatingElements() {
        return originatingElements.values().toArray(Element.EMPTY_ELEMENT_ARRAY);
    }

    public void clear() {
        originatingElements.clear();
    }
}
