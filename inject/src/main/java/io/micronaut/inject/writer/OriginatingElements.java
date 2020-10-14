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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.inject.ast.Element;

/**
 * Interface for types that provide originating elements.
 * @since 2.1.1
 * @author graemerocher
 */
public interface OriginatingElements {
    /**
     * @return The elements where the bean definition originated from as an array.
     * @since 2.1.1
     */
    @NonNull
    Element[] getOriginatingElements();

    /**
     * Add another element that should be included in the originating elements.
     * @param element The element to add
     * @since 2.1.1
     */
    void addOriginatingElement(@NonNull Element element);

    /**
     * Factory to create the originating elements.
     * @param elements The elements
     * @return The originating elements
     */
    static OriginatingElements of(Element...elements) {
        if (Boolean.getBoolean("micronaut.static.originating.elements")) {
            StaticOriginatingElements.INSTANCE.clear();
            for (Element element : elements) {
                StaticOriginatingElements.INSTANCE.addOriginatingElement(element);
            }
            return StaticOriginatingElements.INSTANCE;
        } else {
            return new DefaultOriginatingElements(elements);
        }
    }
}
