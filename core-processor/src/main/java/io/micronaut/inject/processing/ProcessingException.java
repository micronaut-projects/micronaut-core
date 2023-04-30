/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.inject.processing;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.Element;

/**
 * The exception can be used to stop the processing and display an error associated to the element.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
public final class ProcessingException extends RuntimeException {

    private final transient Element originatingElement;

    public ProcessingException(Element element, String message) {
        super(message);
        this.originatingElement = element;
    }

    public ProcessingException(Element originatingElement, String message, Throwable cause) {
        super(message, cause);
        this.originatingElement = originatingElement;
    }

    @Nullable
    public Object getOriginatingElement() {
        if (originatingElement != null) {
            return originatingElement.getNativeType();
        }
        return null;
    }

}
