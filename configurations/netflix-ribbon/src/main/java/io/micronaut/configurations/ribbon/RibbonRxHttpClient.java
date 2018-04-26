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

package io.micronaut.configurations.ribbon;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.reactive.ExecutionListener;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import hu.akarnokd.rxjava.interop.RxJavaInterop;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.DefaultHttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.ssl.NettyClientSslBuilder;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.netty.channel.NettyThreadFactory;
import io.reactivex.Flowable;
import rx.Observable;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;

/**
 * Extended version of {@link DefaultHttpClient} adapted to Ribbon.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Prototype
@Requires(classes = IClientConfig.class)
@Primary
@Replaces(DefaultHttpClient.class)
public class RibbonRxHttpClient extends DefaultHttpClient {

    private final RibbonLoadBalancer loadBalancer;
    private final List<? extends ExecutionListener<?, HttpResponse<?>>> executionListeners;

    /**
     * Constructor.
     * @param loadBalancer loadBalancer
     * @param configuration configuration
     * @param threadFactory threadFactory
     * @param nettyClientSslBuilder nettyClientSslBuilder
     * @param codecRegistry codecRegistry
     * @param executionListeners executionListeners
     * @param annotationMetadataResolver annotationMetadataResolver
     * @param filters filters
     */
    @Inject
    public RibbonRxHttpClient(
        @Parameter LoadBalancer loadBalancer,
        @Parameter HttpClientConfiguration configuration,
        @Named(NettyThreadFactory.NAME) @Nullable ThreadFactory threadFactory,
        NettyClientSslBuilder nettyClientSslBuilder,
        MediaTypeCodecRegistry codecRegistry,
        RibbonExecutionListenerAdapter[] executionListeners,
        @Nullable AnnotationMetadataResolver annotationMetadataResolver,
        HttpClientFilter... filters) {

        super(
                loadBalancer,
                configuration,
                threadFactory,
                nettyClientSslBuilder,
                codecRegistry,
                annotationMetadataResolver,
                filters);
        this.executionListeners = Arrays.asList(executionListeners);
        if (loadBalancer instanceof RibbonLoadBalancer) {
            this.loadBalancer = (RibbonLoadBalancer) loadBalancer;
        } else {
            this.loadBalancer = null;
        }
    }

    /**
     * @return The {@link RibbonLoadBalancer} if one is configured for this client
     */
    public Optional<RibbonLoadBalancer> getLoadBalancer() {
        return Optional.ofNullable(loadBalancer);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I, O> Flowable<HttpResponse<O>> exchange(HttpRequest<I> request, Argument<O> bodyType) {
        if (loadBalancer != null) {
            LoadBalancerCommand<HttpResponse<O>> loadBalancerCommand = buildLoadBalancerCommand();
            Observable<HttpResponse<O>> requestOperation = loadBalancerCommand.submit(server -> {
                URI newURI = loadBalancer.getLoadBalancerContext().reconstructURIWithServer(server, request.getUri());
                return RxJavaInterop.toV1Observable(
                    Flowable.fromPublisher(Publishers.just(newURI))
                        .switchMap(super.buildExchangePublisher(request, bodyType))
                );
            });

            return RxJavaInterop.toV2Flowable(requestOperation);
        } else {
            return super.exchange(request, bodyType);
        }
    }

    @Override
    public <I> Flowable<HttpResponse<ByteBuffer<?>>> exchangeStream(HttpRequest<I> request) {
        if (loadBalancer != null) {

            LoadBalancerCommand<HttpResponse<ByteBuffer<?>>> loadBalancerCommand = buildLoadBalancerCommand();
            Observable<HttpResponse<ByteBuffer<?>>> requestOperation = loadBalancerCommand.submit(server -> {
                URI newURI = loadBalancer.getLoadBalancerContext().reconstructURIWithServer(server, request.getUri());
                return RxJavaInterop.toV1Observable(
                    Flowable.fromPublisher(Publishers.just(newURI))
                        .switchMap(super.buildExchangeStreamPublisher(request))
                );
            });
            return RxJavaInterop.toV2Flowable(requestOperation);
        } else {
            return super.exchangeStream(request);
        }
    }

    @Override
    public <I> Flowable<ByteBuffer<?>> dataStream(HttpRequest<I> request) {
        if (loadBalancer != null) {
            LoadBalancerCommand<ByteBuffer<?>> loadBalancerCommand = buildLoadBalancerCommand();
            Observable<ByteBuffer<?>> requestOperation = loadBalancerCommand.submit(server -> {
                URI newURI = loadBalancer.getLoadBalancerContext().reconstructURIWithServer(server, request.getUri());
                return RxJavaInterop.toV1Observable(
                    Flowable.fromPublisher(Publishers.just(newURI))
                        .switchMap(super.buildDataStreamPublisher(request))
                );
            });
            return RxJavaInterop.toV2Flowable(requestOperation);
        } else {
            return super.dataStream(request);
        }
    }

    @Override
    public <I, O> Flowable<O> jsonStream(HttpRequest<I> request, Argument<O> type) {
        if (loadBalancer != null) {
            LoadBalancerCommand<O> loadBalancerCommand = buildLoadBalancerCommand();
            Observable<O> requestOperation = loadBalancerCommand.submit(server -> {
                URI newURI = loadBalancer.getLoadBalancerContext().reconstructURIWithServer(server, request.getUri());
                return RxJavaInterop.toV1Observable(
                    Flowable.fromPublisher(Publishers.just(newURI))
                        .switchMap(super.buildJsonStreamPublisher(request, type))
                );
            });
            return RxJavaInterop.toV2Flowable(requestOperation);
        } else {
            return super.jsonStream(request, type);
        }
    }

    /**
     * Build the command using the load balancer builder.
     * @param <O> entity type of the command
     * @return entity type
     */
    protected <O> LoadBalancerCommand<O> buildLoadBalancerCommand() {
        LoadBalancerCommand.Builder<O> commandBuilder = LoadBalancerCommand.builder();
        commandBuilder.withLoadBalancer(loadBalancer.getLoadBalancer())
            .withClientConfig(loadBalancer.getClientConfig());

        if (!executionListeners.isEmpty()) {
            commandBuilder.withListeners((List<? extends ExecutionListener<?, O>>) executionListeners);
        }

        return commandBuilder.build();
    }
}
