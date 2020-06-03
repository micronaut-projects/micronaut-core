/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.websocket.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Bean;
import io.micronaut.websocket.WebSocketVersion;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Stereotype meta-annotation declared on both {@link ServerWebSocket} and {@link ClientWebSocket}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.ANNOTATION_TYPE})
public @interface WebSocketComponent {
    /**
     * The default WebSocket URI.
     */
    String DEFAULT_URI = "/ws";

    /**
     * @return The URI of the action
     */
    @AliasFor(member = "uri")
    String value() default DEFAULT_URI;

    /**
     * @return The URI of the action
     */
    @AliasFor(member = "value")
    String uri() default DEFAULT_URI;

    /**
     * @return The WebSocket version to use to connect
     */
    WebSocketVersion version() default WebSocketVersion.V13;
}
