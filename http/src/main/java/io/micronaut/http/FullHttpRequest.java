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
package io.micronaut.http;

import io.micronaut.core.type.Argument;

import java.util.Optional;

/**
 * A request wrapper with knowledge of the body argument.
 *
 * @param <B> The body type
 * @author James Kleeh
 * @since 1.1.0
 */
public class FullHttpRequest<B> extends HttpRequestWrapper<B> {

    private final Argument<B> bodyType;

    /**
     * @param delegate The Http Request
     * @param bodyType The Body Type
     */
    public FullHttpRequest(HttpRequest<B> delegate,
                           Argument<B> bodyType) {
        super(delegate);
        this.bodyType = bodyType;
    }

    @Override
    public Optional<B> getBody() {
        return super.getBody(bodyType);
    }
}
