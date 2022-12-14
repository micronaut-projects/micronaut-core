/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.netty.multipart.NettyPartData;
import io.micronaut.http.server.netty.multipart.NettyStreamingFileUpload;
import io.micronaut.web.router.RouteMatch;
import io.netty.buffer.ByteBufHolder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Extension of {@link BaseRouteCompleter} that handles incoming multipart data and binds
 * parameters (e.g. {@link io.micronaut.http.annotation.Part}).
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
final class FormRouteCompleter extends BaseRouteCompleter {
    static final Argument<PartData> ARGUMENT_PART_DATA = Argument.of(PartData.class);
    private static final Logger LOG = LoggerFactory.getLogger(FormRouteCompleter.class);

    private final NettyStreamingFileUpload.Factory fileUploadFactory;
    private final ConversionService conversionService;
    private final boolean alwaysAddContent = request.isFormData();
    private final AtomicLong pressureRequested = new AtomicLong();
    private final Map<String, Sinks.Many<Object>> subjectsByDataName = new HashMap<>();
    private final Collection<Sinks.Many<?>> downstreamSubscribers = new ArrayList<>();

    FormRouteCompleter(NettyStreamingFileUpload.Factory fileUploadFactory, ConversionService conversionService, NettyHttpRequest<?> request, RouteMatch<?> routeMatch) {
        super(request, routeMatch);
        this.fileUploadFactory = fileUploadFactory;
        this.conversionService = conversionService;
    }

    private void request(long n) {
        pressureRequested.getAndUpdate(old -> {
            if ((old + n) < old) {
                return Long.MAX_VALUE;
            } else {
                return old + n;
            }
        });
        needsInput = true;
        Runnable checkDemand = this.checkDemand;
        if (checkDemand != null) {
            checkDemand.run();
        }
    }

    private <T> Flux<T> withFlowControl(Flux<T> flux, MicronautHttpData<?> data) {
        return flux
            .doOnComplete(data::release)
            .doOnRequest(this::request);
    }

    @Override
    protected void addHolder(ByteBufHolder holder) {
        if (holder instanceof HttpData data) {
            needsInput = pressureRequested.decrementAndGet() > 0;
            addData((MicronautHttpData<?>) data);
        } else {
            super.addHolder(holder);
        }
    }

    @Override
    void completeSuccess() {
        for (Sinks.Many<?> subject : downstreamSubscribers) {
            // subjects will ignore the onComplete if they're already done
            subject.tryEmitComplete();
        }
        super.completeSuccess();
    }

    @Override
    void completeFailure(Throwable failure) {
        super.completeFailure(failure);
        for (Sinks.Many<?> subject : downstreamSubscribers) {
            subject.tryEmitError(failure);
        }
    }

    private void addData(MicronautHttpData<?> data) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received HTTP Data for request [{}]: {}", request, data);
        }

        String name = data.getName();
        Optional<Argument<?>> requiredInput = routeMatch.getRequiredInput(name);

        if (requiredInput.isEmpty()) {
            request.addContent(data);
            request(1);
            return;
        }

        Argument<?> argument = requiredInput.get();
        Supplier<Object> value;
        boolean isPublisher = Publishers.isConvertibleToPublisher(argument.getType());
        boolean chunkedProcessing = false;

        if (isPublisher) {
            if (data.attachment == null) {
                data.attachment = new HttpDataAttachment();
                // retain exactly once
                data.retain();
            }

            Argument typeVariable;

            if (StreamingFileUpload.class.isAssignableFrom(argument.getType())) {
                typeVariable = ARGUMENT_PART_DATA;
            } else {
                typeVariable = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            }
            Class<?> typeVariableType = typeVariable.getType();

            Sinks.Many<Object> namedSubject = subjectsByDataName.computeIfAbsent(name, key -> makeDownstreamUnicastProcessor());

            chunkedProcessing = PartData.class.equals(typeVariableType) ||
                Publishers.isConvertibleToPublisher(typeVariableType) ||
                ClassUtils.isJavaLangType(typeVariableType);

            if (Publishers.isConvertibleToPublisher(typeVariableType)) {
                boolean streamingFileUpload = StreamingFileUpload.class.isAssignableFrom(typeVariableType);
                if (streamingFileUpload) {
                    typeVariable = ARGUMENT_PART_DATA;
                } else {
                    typeVariable = typeVariable.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                }
                if (data.attachment.subject == null) {
                    Sinks.Many<PartData> childSubject = makeDownstreamUnicastProcessor();
                    Flux<PartData> flowable = withFlowControl(childSubject.asFlux(), data);
                    if (streamingFileUpload && data instanceof FileUpload fu) {
                        namedSubject.tryEmitNext(fileUploadFactory.create(fu, flowable));
                    } else {
                        namedSubject.tryEmitNext(flowable);
                    }

                    data.attachment.subject = childSubject;
                }
            }

            Sinks.Many subject;

            if (data.attachment.subject != null) {
                subject = data.attachment.subject;
            } else {
                subject = namedSubject;
            }

            Object part = data;

            if (chunkedProcessing) {
                MicronautHttpData<?>.Chunk chunk = data.pollChunk();
                part = new NettyPartData(() -> {
                    if (data instanceof FileUpload fu) {
                        return Optional.of(MediaType.of(fu.getContentType()));
                    } else {
                        return Optional.empty();
                    }
                }, chunk::claim);
            }

            if (data instanceof FileUpload fu &&
                StreamingFileUpload.class.isAssignableFrom(argument.getType()) &&
                data.attachment.upload == null) {

                data.attachment.upload = fileUploadFactory.create(fu, withFlowControl(subject.asFlux(), data));
            }

            Optional<?> converted = conversionService.convert(part, typeVariable);

            converted.ifPresent(subject::tryEmitNext);

            if (data.isCompleted() && chunkedProcessing) {
                subject.tryEmitComplete();
            }

            value = () -> {
                if (data.attachment.upload != null) {
                    return data.attachment.upload;
                } else {
                    if (data.attachment.subject == null) {
                        return withFlowControl(namedSubject.asFlux(), data);
                    } else {
                        return namedSubject.asFlux();
                    }
                }
            };

        } else {
            if (data instanceof Attribute && !data.isCompleted()) {
                request.addContent(data);
                request(1);
                return;
            } else {
                value = () -> {
                    if (data.refCnt() > 0) {
                        return data;
                    } else {
                        return null;
                    }
                };
            }
        }

        if (!execute) {
            String argumentName = argument.getName();
            if (!routeMatch.isSatisfied(argumentName)) {
                Object fulfillParamter = value.get();
                routeMatch = routeMatch.fulfill(Collections.singletonMap(argumentName, fulfillParamter));
                // we need to release the data here. However, if the route argument is a
                // ByteBuffer, we need to retain the data until the route is executed. Adding
                // the data to the request ensures it is cleaned up after the route completes.
                if (!alwaysAddContent && fulfillParamter instanceof ByteBufHolder holder) {
                    request.addContent(holder);
                }
            }
            if (isPublisher && chunkedProcessing) {
                //accounting for the previous request
                request(1);
            }
            if (routeMatch.isExecutable()) {
                execute = true;
            }
        }

        if (alwaysAddContent && !request.destroyed) {
            request.addContent(data);
        }

        if (!execute || !chunkedProcessing) {
            request(1);
        }
    }

    private <T> Sinks.Many<T> makeDownstreamUnicastProcessor() {
        Sinks.Many<T> processor = Sinks.many().unicast().onBackpressureBuffer();
        downstreamSubscribers.add(processor);
        return processor;
    }

    static class HttpDataAttachment {
        private Sinks.Many<?> subject;
        private StreamingFileUpload upload;
    }
}
