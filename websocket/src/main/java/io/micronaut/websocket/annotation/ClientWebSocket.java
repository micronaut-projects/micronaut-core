/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.*;
import io.micronaut.websocket.interceptor.ClientWebSocketInterceptor;
import io.micronaut.websocket.interceptor.WebSocketSessionAware;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation declared on the client to indicate the class handles web socket frames.
 *
 * @author graemerocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Bean
@DefaultScope(Prototype.class)
@Introduction(interfaces = WebSocketSessionAware.class)
@Type(ClientWebSocketInterceptor.class)
public @interface ClientWebSocket {
    /**
     * The default websocket URI.
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
    String version() default "13";
}
