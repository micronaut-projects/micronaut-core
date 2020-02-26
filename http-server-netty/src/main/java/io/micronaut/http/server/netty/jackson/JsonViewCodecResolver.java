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
package io.micronaut.http.server.netty.jackson;

import io.micronaut.jackson.codec.JsonMediaTypeCodec;

import javax.annotation.Nonnull;

/**
 * Interface for resolving codecs for {@link com.fasterxml.jackson.annotation.JsonView} types.
 *
 * @author graemerocher
 * @since 1.1
 */
public interface JsonViewCodecResolver {
    /**
     * Resolves a {@link JsonMediaTypeCodec} for the view class (specified as the JsonView annotation value).
     * @param viewClass The view class
     * @return The codec
     */
    @Nonnull JsonMediaTypeCodec resolveJsonViewCodec(@Nonnull Class<?> viewClass);
}
