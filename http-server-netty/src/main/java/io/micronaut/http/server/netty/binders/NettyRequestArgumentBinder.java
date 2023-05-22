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
package io.micronaut.http.server.netty.binders;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.server.netty.NettyHttpRequest;

import java.util.Optional;

/**
 * A version of {@link RequestArgumentBinder} that requires {@link NettyHttpRequest}.
 * @param <T> A type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Experimental
public sealed interface NettyRequestArgumentBinder<T> extends RequestArgumentBinder<T>
    permits NettyCompletedFileUploadBinder, NettyPublisherPartUploadBinder, NettyStreamingFileUploadBinder {

    @Override
    default BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        if (source instanceof NettyHttpRequest<?> nettyHttpRequest) {
            return bindForNettyRequest(context, nettyHttpRequest);
        }
        return BindingResult.EMPTY;
    }

    /**
     * Bind the given argument from the given source.
     *
     * @param context             The {@link ArgumentConversionContext}
     * @param nettyHttpRequest    The netty http request
     * @return An {@link Optional} of the value. If no binding was possible {@link Optional#empty()}
     */
    BindingResult<T> bindForNettyRequest(ArgumentConversionContext<T> context, NettyHttpRequest<?> nettyHttpRequest);
}
