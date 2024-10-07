/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.netty.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.body.ByteBody;
import io.netty.handler.codec.http.HttpResponse;

/**
 * This interface is used to write the different kinds of netty responses.
 *
 * @since 4.0.0
 */
@Internal
@Experimental
public interface NettyWriteContext {
    /**
     * Write a response.
     *
     * @param response The response status, headers etc
     * @param body     The response body
     */
    void write(@NonNull HttpResponse response, @NonNull ByteBody body);

    /**
     * Write a response to a {@code HEAD} request. This is special because it never has a body but
     * may still have a non-zero {@code Content-Length} header.
     *
     * @param response The response status, headers etc
     */
    void writeHeadResponse(@NonNull HttpResponse response);
}
