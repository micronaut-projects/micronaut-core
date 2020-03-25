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
package io.micronaut.http.server.netty;

import io.micronaut.context.BeanLocator;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.inject.ExecutionHandle;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.qualifier.ConsumesMediaTypeQualifier;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;


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
class DefaultHttpContentProcessorResolver implements HttpContentProcessorResolver {

    private static final Set<Class> RAW_BODY_TYPES = CollectionUtils.setOf(String.class, byte[].class, ByteBuffer.class);

    private final BeanLocator beanLocator;
    private final NettyHttpServerConfiguration serverConfiguration;

    /**
     * @param beanLocator         The bean locator to search for processors with
     * @param serverConfiguration The server configuration
     */
    DefaultHttpContentProcessorResolver(BeanLocator beanLocator,
                                        NettyHttpServerConfiguration serverConfiguration) {
        this.beanLocator = beanLocator;
        this.serverConfiguration = serverConfiguration;
    }

    @Override
    @NonNull
    public HttpContentProcessor<?> resolve(@NonNull NettyHttpRequest<?> request, @NonNull RouteMatch<?> route) {
        Argument<?> bodyType = route.getBodyArgument()
                /*
                The getBodyArgument() method returns arguments for functions where it is
                not possible to dictate whether the argument is supposed to bind the entire
                body or just a part of the body. We check to ensure the argument has the body
                annotation to exclude that use case
                */
                .filter(argument -> {
                    AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
                    if (annotationMetadata.hasAnnotation(Body.class)) {
                        return !annotationMetadata.stringValue(Body.class).isPresent();
                    } else {
                        return false;
                    }
                })
                .orElseGet(() -> {
                    if (route instanceof ExecutionHandle) {
                        for (Argument<?> argument: ((ExecutionHandle) route).getArguments()) {
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
    public HttpContentProcessor<?> resolve(@NonNull NettyHttpRequest<?> request, @NonNull Argument<?> bodyType) {
        if (bodyType.getType() == HttpRequest.class) {
            bodyType = bodyType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        }
        boolean isRaw = RAW_BODY_TYPES.contains(bodyType.getType());
        return resolve(request, isRaw);
    }

    @Override
    @NonNull
    public HttpContentProcessor<?> resolve(@NonNull NettyHttpRequest<?> request) {
        return resolve(request, false);
    }

    private HttpContentProcessor<?> resolve(NettyHttpRequest<?> request, boolean rawBodyType) {
        Supplier<DefaultHttpContentProcessor> defaultHttpContentProcessor = () -> new DefaultHttpContentProcessor(request, serverConfiguration);

        if (rawBodyType) {
            return defaultHttpContentProcessor.get();
        } else {
            Optional<MediaType> contentType = request.getContentType();
            return contentType
                    .flatMap(type ->
                            beanLocator.findBean(HttpContentSubscriberFactory.class,
                                    new ConsumesMediaTypeQualifier<>(type))
                    ).map(factory ->
                            factory.build(request)
                    ).orElseGet(defaultHttpContentProcessor);
        }
    }

}
