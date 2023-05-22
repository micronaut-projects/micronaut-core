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
package io.micronaut.http.server.netty;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.CopyOnWriteMap;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.inject.ExecutionHandle;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.qualifier.ConsumesMediaTypeQualifier;
import jakarta.inject.Singleton;

import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;


/**
 * Default implementation that determines if the body argument (if present) does not expect
 * decoding of the request body, that the {@link DefaultHttpContentProcessor} should be used,
 * regardless of the content type of the request.
 *
 * If the body argument dictates decoding should occur, the processor that corresponds to
 * the request content type will be returned.
 *
 * @author James Kleeh
 * @since 1.3.0
 */
@Singleton
@Internal
public class DefaultHttpContentProcessorResolver implements HttpContentProcessorResolver {

    private static final Set<Class<?>> RAW_BODY_TYPES = CollectionUtils.setOf(String.class, byte[].class, ByteBuffer.class, InputStream.class);

    private final BeanProvider<NettyHttpServerConfiguration> serverConfiguration;
    private final ConcurrentMap<MediaType, Optional<HttpContentSubscriberFactory>> subscriberFactoryCache = new CopyOnWriteMap<>(128);
    private final Function<MediaType, Optional<HttpContentSubscriberFactory>> findSubscriberFactory;
    private NettyHttpServerConfiguration nettyServerConfiguration;

    /**
     * @param beanLocator         The bean locator to search for processors with
     * @param serverConfiguration The server configuration
     */
    DefaultHttpContentProcessorResolver(BeanLocator beanLocator,
                                        BeanProvider<NettyHttpServerConfiguration> serverConfiguration) {
        this.serverConfiguration = serverConfiguration;
        this.findSubscriberFactory = mt -> beanLocator.findBean(HttpContentSubscriberFactory.class, new ConsumesMediaTypeQualifier<>(mt));
    }

    @Override
    @NonNull
    @Deprecated
    public HttpContentProcessor resolve(@NonNull NettyHttpRequest<?> request, @NonNull RouteMatch<?> route) {
        Argument<?> bodyType = route.getRouteInfo().getFullRequestBodyType()
                .orElseGet(() -> {
                    if (route instanceof ExecutionHandle executionHandle) {
                        for (Argument<?> argument: executionHandle.getArguments()) {
                            if (argument.getType() == HttpRequest.class) {
                                return argument;
                            }
                        }
                    }
                    return Argument.OBJECT_ARGUMENT;
                });
        return resolve(request, bodyType);
    }

    @Override
    @NonNull
    public HttpContentProcessor resolve(@NonNull NettyHttpRequest<?> request, @NonNull Argument<?> bodyType) {
        if (bodyType.getType() == HttpRequest.class) {
            bodyType = bodyType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        }
        return resolve(request, isRaw(bodyType));
    }

    public static boolean isRaw(Argument<?> bodyType) {
        return RAW_BODY_TYPES.contains(bodyType.getType());
    }

    @Override
    @NonNull
    public HttpContentProcessor resolve(@NonNull NettyHttpRequest<?> request) {
        return resolve(request, false);
    }

    private HttpContentProcessor resolve(NettyHttpRequest<?> request, boolean rawBodyType) {
        if (!rawBodyType) {
            Optional<MediaType> contentType = request.getContentType();
            if (contentType.isPresent()) {
                Optional<HttpContentSubscriberFactory> factory = subscriberFactoryCache.computeIfAbsent(contentType.get(), findSubscriberFactory);
                if (factory.isPresent()) {
                    return factory.get().build(request);
                }
            }
        }
        return new DefaultHttpContentProcessor(request, getServerConfiguration());
    }

    private NettyHttpServerConfiguration getServerConfiguration() {
        NettyHttpServerConfiguration nettyHttpServerConfiguration = this.nettyServerConfiguration;
        if (nettyHttpServerConfiguration == null) {
            synchronized (this) { // double check
                nettyHttpServerConfiguration = this.nettyServerConfiguration;
                if (nettyHttpServerConfiguration == null) {
                    nettyHttpServerConfiguration = serverConfiguration.get();
                    this.nettyServerConfiguration = nettyHttpServerConfiguration;
                }
            }
        }
        return nettyHttpServerConfiguration;
    }
}
