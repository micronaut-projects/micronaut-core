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

import io.micronaut.annotation.processing.visitor.AbstractJavaElement;
import io.micronaut.annotation.processing.visitor.JavaNativeElement;
import io.micronaut.core.annotation.Nullable;

import javax.lang.model.element.Element;

/**
 * Exception to indicate postponing processing to next round.
 */
public final class PostponeToNextRoundException extends RuntimeException {

    private final transient Object errorElement;
    private final String path;

    /**
     * @param originatingElement Teh originating element
     * @param path The originating element path
     */
    public PostponeToNextRoundException(Object originatingElement, String path) {
        this.errorElement = originatingElement;
        this.path = path;
    }

    public Object getErrorElement() {
        return errorElement;
    }

    public @Nullable Element getNativeErrorElement() {
        return resolvedFailedElement(errorElement);
    }

    public static Element resolvedFailedElement(Object errorElement) {
        Element failedElement;
        if (errorElement instanceof Element el) {
            failedElement = el;
        } else if (errorElement instanceof JavaNativeElement jne) {
            failedElement = jne.element();
        } else if (errorElement instanceof AbstractJavaElement aje) {
            failedElement = aje.getNativeType().element();
        } else {
            failedElement = null;
        }
        return failedElement;
    }

    public String getPath() {
        return path;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        // no-op: flow control exception
        return this;
    }
}
