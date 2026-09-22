/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.multipart.FormFactory;

import java.nio.charset.Charset;
import java.util.concurrent.Executor;

/**
 * What the form content of a request needs: the executor for disk work, the byte body factory,
 * the charset and the limits.
 *
 * @param formFactory      The form factory
 * @param ioExecutor       The executor for blocking disk work
 * @param byteBodyFactory  The byte body factory of the request
 * @param charset          The charset of the request
 * @param maxBufferSize    The limit of content buffered in memory without an explicit limit
 * @param maxFileSize      The limit of an uploaded file
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record UploadContext(FormFactory formFactory,
                     Executor ioExecutor,
                     ByteBodyFactory byteBodyFactory,
                     Charset charset,
                     int maxBufferSize,
                     long maxFileSize) {

    static UploadContext of(FormFactory formFactory, FormCapableHttpRequest<?> request) {
        HttpServerConfiguration configuration = formFactory.getConfiguration();
        return new UploadContext(
            formFactory,
            formFactory.getDiskWriteExecutor(),
            request.byteBodyFactory(),
            request.getCharacterEncoding(),
            (int) Math.min(Integer.MAX_VALUE - 8, Math.max(0, configuration.getMaxRequestBufferSize())),
            configuration.getMultipart().getMaxFileSize()
        );
    }
}
