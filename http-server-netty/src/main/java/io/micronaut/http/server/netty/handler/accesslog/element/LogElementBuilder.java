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
package io.micronaut.http.server.netty.handler.accesslog.element;

/**
 * Builder for LogElement.
 *
 * @author croudet
 * @since 2.0
 */
public interface LogElementBuilder {

    /**
     * Builds the log element for the specified token. It should return null it the token is not supported.
     *
     * @param token The log element marker.
     * @param param An optional paramter.
     * @return A LogElement or null if not supported by the builder.
     */
    LogElement build(String token, String param);
}
