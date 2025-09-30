/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.body;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;

import java.io.InputStream;

/**
 * The body handler for input stream.
 *
 * @author Denis Stepanov
 * @since 4.10
 */
@Prototype
@BootstrapContextCompatible
@Internal
final class InputStreamBodyReader implements TypedMessageBodyReader<InputStream> {

    @Override
    public InputStream read(Argument<InputStream> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        return inputStream;
    }

    @Override
    public Argument<InputStream> getType() {
        return Argument.of(InputStream.class);
    }
}
