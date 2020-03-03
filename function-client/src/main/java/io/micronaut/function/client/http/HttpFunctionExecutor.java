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
package io.micronaut.function.client.http;

import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.function.client.FunctionDefinition;
import io.micronaut.function.client.FunctionInvoker;
import io.micronaut.function.client.FunctionInvokerChooser;
import io.micronaut.function.client.exceptions.FunctionExecutionException;
import io.micronaut.function.client.exceptions.FunctionNotFoundException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.DefaultHttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.filter.HttpClientFilterResolver;
import io.micronaut.http.client.ssl.NettyClientSslBuilder;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.netty.channel.NettyThreadFactory;
import org.reactivestreams.Publisher;

import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;

/**
 * A {@link io.micronaut.function.executor.FunctionExecutor} that uses a {@link io.micronaut.http.client.HttpClient} to execute a remote function definition.
 *
 * @param <I> input type
 * @param <O> output type
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class HttpFunctionExecutor<I, O> implements FunctionInvoker<I, O>, Closeable, FunctionInvokerChooser {

    private final DefaultHttpClient httpClient;

    /**
     * Constructor.
     * @param configuration configuration
     * @param threadFactory threadFactory
     * @param nettyClientSslBuilder nettyClientSslBuilder
     * @param codecRegistry codecRegistry
     * @param annotationMetadataResolver annotationMetadataResolver
     * @param filters filters
     */
    public HttpFunctionExecutor(
            HttpClientConfiguration configuration,
            @Named(NettyThreadFactory.NAME) ThreadFactory threadFactory,
            NettyClientSslBuilder nettyClientSslBuilder,
            MediaTypeCodecRegistry codecRegistry,
            AnnotationMetadataResolver annotationMetadataResolver,
            HttpClientFilter... filters) {
        super();
        this.httpClient = new DefaultHttpClient(
            LoadBalancer.empty(),
            configuration,
            null,
            new HttpClientFilterResolver(null, null, annotationMetadataResolver, Arrays.asList(filters)),
            threadFactory,
            nettyClientSslBuilder,
            codecRegistry
        );
    }

    @Override
    public O invoke(FunctionDefinition definition, I input, Argument<O> outputType) {
        Optional<URI> opt = definition.getURI();
        if (!opt.isPresent()) {
            throw new FunctionNotFoundException(definition.getName());
        } else {
            URI uri = opt.get();
            HttpRequest request;
            if (input == null) {
                request = HttpRequest.GET(uri.toString());
            } else {
                request = HttpRequest.POST(uri.toString(), input);
            }

            if (input != null && ClassUtils.isJavaLangType(input.getClass())) {
                ((MutableHttpRequest) request).contentType(MediaType.TEXT_PLAIN_TYPE);
            }

            Class<O> outputJavaType = outputType.getType();

            if (ClassUtils.isJavaLangType(outputJavaType)) {
                ((MutableHttpRequest) request).accept(MediaType.TEXT_PLAIN_TYPE);
            }

            if (Publishers.isConvertibleToPublisher(outputJavaType)) {
                Publisher publisher = httpClient.retrieve(request, outputType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT));
                return ConversionService.SHARED.convert(publisher, outputType).orElseThrow(() ->
                    new FunctionExecutionException("Unsupported Reactive type: " + outputJavaType)
                );
            } else {
                return (O) httpClient.toBlocking().retrieve(request, outputType);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I1, O2> Optional<FunctionInvoker<I1, O2>> choose(FunctionDefinition definition) {
        if (definition.getURI().isPresent()) {
            return Optional.of((FunctionInvoker) this);
        }

        return Optional.empty();
    }

    @Override
    @PreDestroy
    public void close() throws IOException {
        httpClient.close();
    }
}
