/*
 * Copyright 2018 original authors
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
package org.particleframework.configurations.ribbon;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.client.config.IClientConfigKey;
import com.netflix.loadbalancer.LoadBalancerContext;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.reactive.LoadBalancerCommand;
import com.netflix.loadbalancer.reactive.ServerOperation;
import hu.akarnokd.rxjava.interop.RxJavaInterop;
import io.reactivex.Flowable;
import org.particleframework.context.annotation.Primary;
import org.particleframework.context.annotation.Prototype;
import org.particleframework.context.annotation.Replaces;
import org.particleframework.context.annotation.Requires;
import org.particleframework.core.async.publisher.Publishers;
import org.particleframework.core.io.buffer.ByteBuffer;
import org.particleframework.core.type.Argument;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.client.HttpClientConfiguration;
import org.particleframework.http.client.LoadBalancer;
import org.particleframework.http.client.rxjava2.RxHttpClient;
import org.particleframework.http.codec.MediaTypeCodecRegistry;
import org.particleframework.http.filter.HttpClientFilter;
import rx.Observable;

import javax.inject.Inject;
import java.net.URI;
import java.util.Optional;

/**
 * Extended version of {@link RxHttpClient} adapted to Ribbon
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Prototype
@Requires(classes = IClientConfig.class)
@Primary
@Replaces(RxHttpClient.class)
public class RibbonRxHttpClient extends RxHttpClient {

    private final RibbonLoadBalancer loadBalancer;

    @Inject
    public RibbonRxHttpClient(
            @org.particleframework.context.annotation.Argument LoadBalancer loadBalancer,
            @org.particleframework.context.annotation.Argument HttpClientConfiguration configuration,
            MediaTypeCodecRegistry codecRegistry,
            HttpClientFilter... filters) {
        super(loadBalancer, configuration, codecRegistry, filters);
        if(loadBalancer  instanceof RibbonLoadBalancer) {
            this.loadBalancer = (RibbonLoadBalancer) loadBalancer;
        }
        else {
            this.loadBalancer = null;
        }
    }

    /**
     * @return The {@link RibbonLoadBalancer} if one is configured for this client
     */
    public Optional<RibbonLoadBalancer> getLoadBalancer() {
        return Optional.ofNullable(loadBalancer);
    }

    @Override
    public <I, O> Flowable<HttpResponse<O>> exchange(HttpRequest<I> request, Argument<O> bodyType) {
        if(isLoadBalancing()) {

            LoadBalancerCommand.Builder<HttpResponse<O>> commandBuilder = LoadBalancerCommand.builder();
            commandBuilder.withLoadBalancer(loadBalancer.getLoadBalancer())
                    .withClientConfig(loadBalancer.getClientConfig());

            LoadBalancerCommand<HttpResponse<O>> loadBalancerCommand = commandBuilder.build();
            Observable<HttpResponse<O>> requestOperation = loadBalancerCommand.submit(server -> {
                URI newURI = loadBalancer.getLoadBalancerContext().reconstructURIWithServer(server, request.getUri());
                return RxJavaInterop.toV1Observable(
                        Flowable.fromPublisher(Publishers.just(newURI))
                                .switchMap(super.buildExchangePublisher(request, bodyType))
                );
            });

            return RxJavaInterop.toV2Flowable(requestOperation);
        }
        else {
            return super.exchange(request, bodyType);
        }
    }

    protected boolean isLoadBalancing() {
        return loadBalancer != null && loadBalancer.getClientConfig().getPropertyAsBoolean(CommonClientConfigKey.InitializeNFLoadBalancer, true);
    }
}