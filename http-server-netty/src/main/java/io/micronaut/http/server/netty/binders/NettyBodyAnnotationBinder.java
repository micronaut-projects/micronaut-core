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
package io.micronaut.http.server.netty.binders;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Replaces;
import jakarta.inject.Singleton;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.server.binding.ServerBodyAnnotationBinder;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.converters.NettyConverters;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Netty native conversion and legacy decoded-body state for the shared server binder.
 *
 * @param <T> The bound type
 */
@Internal
@Singleton
@Replaces(ServerBodyAnnotationBinder.class)
final class NettyBodyAnnotationBinder<T> extends ServerBodyAnnotationBinder<T> {
    NettyBodyAnnotationBinder(ConversionService conversionService,
                              MessageBodyHandlerRegistry bodyHandlerRegistry,
                              BeanProvider<FormFactory> formFactory) {
        super(conversionService, bodyHandlerRegistry, formFactory);
    }

    @Override
    public @Nullable ServerHttpRequest<?> bodyOf(HttpRequest<?> source) {
        return source instanceof NettyHttpRequest<?> netty ? netty : super.bodyOf(source);
    }

    @Override
    protected BindingResult<ConvertibleValues<?>> bindFullBodyConvertibleValues(HttpRequest<?> source) {
        if (!(source instanceof NettyHttpRequest<?> request)) {
            return super.bindFullBodyConvertibleValues(source);
        }
        BindingResult<ConvertibleValues<?>> existing = request.convertibleBody;
        if (existing != null) {
            return existing;
        }
        @SuppressWarnings("unchecked")
        BindingResult<ConvertibleValues<?>> result = (BindingResult<ConvertibleValues<?>>) bindFullBody(
            (ArgumentConversionContext<T>) ConversionContext.of(ConvertibleValues.class), request);
        request.convertibleBody = result;
        return result;
    }

    @Override
    protected @Nullable FormCapableHttpRequest<?> formRequest(ServerHttpRequest<?> server) {
        NettyHttpRequest<?> original = server instanceof NettyHttpRequest<?> netty ? netty : NettyHttpRequest.findBodyRequest(server);
        return original == null ? super.formRequest(server) : original;
    }

    @Override
    protected void cacheDecodedBody(HttpRequest<?> request, ServerHttpRequest<?> server, @Nullable Object value) {
        NettyHttpRequest<?> original = server instanceof NettyHttpRequest<?> netty ? netty : NettyHttpRequest.findBodyRequest(server);
        if (original != null && !(request instanceof HttpRequestWrapper<?>) && NettyHttpRequest.findBodyRequest(request) == original) {
            original.setLegacyBody(value);
        }
    }

    @Override
    protected Optional<T> convertNative(ArgumentConversionContext<T> context, AvailableByteBody body) {
        ByteBuf buffer = NettyByteBodyFactory.toByteBuf(body);
        Optional<T> converted = conversionService.convert(buffer, ByteBuf.class, context.getArgument().getType(), context);
        NettyConverters.postProcess(buffer, converted);
        return converted;
    }
}
