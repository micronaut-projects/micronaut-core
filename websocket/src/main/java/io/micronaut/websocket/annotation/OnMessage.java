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
package io.micronaut.websocket.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation that can be applied to a method that will receive WebSocket message frames. Largely mirrors the
 * equivalent annotation in {@code javax.websocket} and eventual support for the specification could be added in the future.
 *
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD})
@WebSocketMapping
@Inherited
public @interface OnMessage {

    /**
     * The maximum size of a WebSocket payload.
     * @return The max size
     */
    int maxPayloadLength() default 65536;

    /**
     * Web socket servers must set this to true processed incoming masked payload. Client implementations must set this to false.
     *
     * @return will the server expect masked frames
     */
    boolean expectMaskedFrames() default true;

    /**
     * When set to true, frames which are not masked properly according to the standard will still be  accepted.
     *
     * @return are mask mismatches allowed
     */
    boolean allowMaskMismatch() default false;

    /**
     * Flag to allow reserved extension bits to be used or not
     *
     * @return are extension bits allowed
     */
    boolean allowExtensions() default false;

    /**
     * Should the connection close on any type of protocol violations
     *
     * @return will connections close on exceptions
     */
    boolean closeOnProtocolViolation() default true;

    /**
     * Checks if the message bytes are encoded using UTF-8
     *
     * @return is validator enabled
     */
    boolean withUTF8Validator() default true;
}
