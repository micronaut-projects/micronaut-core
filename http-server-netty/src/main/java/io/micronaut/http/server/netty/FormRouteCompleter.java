package io.micronaut.http.server.netty;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.netty.multipart.NettyPartData;
import io.micronaut.http.server.netty.multipart.NettyStreamingFileUpload;
import io.micronaut.web.router.RouteMatch;
import io.netty.buffer.ByteBufHolder;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import io.netty.util.ReferenceCountUtil;
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

final class FormRouteCompleter {
    private static final Logger LOG = LoggerFactory.getLogger(FormRouteCompleter.class);

    private final RoutingInBoundHandler rib;
    private final NettyHttpRequest<?> request;
    private final FlowControl flowControl;
    RouteMatch<?> routeMatch;

    final boolean alwaysAddContent;
    boolean executed = false;
    final AtomicLong pressureRequested = new AtomicLong();
    final Map<String, Sinks.Many<Object>> subjectsByDataName = new HashMap<>();
    final Collection<Sinks.Many<?>> downstreamSubscribers = new ArrayList<>();

    FormRouteCompleter(RoutingInBoundHandler rib, NettyHttpRequest<?> request, FlowControl flowControl, RouteMatch<?> routeMatch) {
        this.rib = rib;
        this.request = request;
        this.flowControl = flowControl;
        this.routeMatch = routeMatch;
        this.alwaysAddContent = request.isFormData();
    }

    private void request(long n) {
        long oldPressure = pressureRequested.getAndUpdate(old -> {
            if ((old + n) < old) {
                return Long.MAX_VALUE;
            } else {
                return old + n;
            }
        });
        if (oldPressure <= 0) {
            flowControl.read();
        }
    }

    private <T> Flux<T> withFlowControl(Flux<T> flux, MicronautHttpData<?> data) {
        return flux
            .doOnComplete(data::release)
            .doOnRequest(this::request);
    }

    void add(Object message) {
        try {
            if (request.destroyed) {
                // we don't want this message anymore
                return;
            }

            if (message instanceof ByteBufHolder) {
                if (message instanceof HttpData data) {
                    boolean more = pressureRequested.decrementAndGet() > 0;
                    addData((MicronautHttpData<?>) data);
                    if (more) {
                        flowControl.read();
                    }
                } else {
                    request.addContent((ByteBufHolder) message);
                    flowControl.read();
                }
            } else {
                ((NettyHttpRequest) request).setBody(message);
                flowControl.read();
            }

            // now, a pseudo try-finally with addSuppressed.
        } catch (Throwable t) {
            try {
                ReferenceCountUtil.release(message);
            } catch (Throwable u) {
                t.addSuppressed(u);
            }
            throw t;
        }

        // the upstream processor gives us ownership of the message, so we need to release it.
        ReferenceCountUtil.release(message);
    }

    private void addData(MicronautHttpData<?> data) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Received HTTP Data for request [{}]: {}", request, data);
        }

        String name = data.getName();
        Optional<Argument<?>> requiredInput = routeMatch.getRequiredInput(name);

        if (requiredInput.isPresent()) {
            Argument<?> argument = requiredInput.get();
            Supplier<Object> value;
            boolean isPublisher = Publishers.isConvertibleToPublisher(argument.getType());
            boolean chunkedProcessing = false;

            if (isPublisher) {
                if (data.attachment == null) {
                    data.attachment = new HttpDataAttachment();
                }

                Argument typeVariable;

                if (StreamingFileUpload.class.isAssignableFrom(argument.getType())) {
                    typeVariable = RoutingInBoundHandler.ARGUMENT_PART_DATA;
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
                        typeVariable = RoutingInBoundHandler.ARGUMENT_PART_DATA;
                    } else {
                        typeVariable = typeVariable.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                    }
                    if (data.attachment.subject == null) {
                        Sinks.Many<PartData> childSubject = makeDownstreamUnicastProcessor();
                        Flux<PartData> flowable = withFlowControl(childSubject.asFlux(), data);
                        if (streamingFileUpload && data instanceof FileUpload) {
                            namedSubject.tryEmitNext(new NettyStreamingFileUpload(
                                (FileUpload) data,
                                rib.serverConfiguration.getMultipart(),
                                rib.getIoExecutor(),
                                flowable));
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

                if (data instanceof FileUpload &&
                    StreamingFileUpload.class.isAssignableFrom(argument.getType())) {
                    if (data.attachment.upload == null) {
                        data.attachment.upload = new NettyStreamingFileUpload(
                            (FileUpload) data,
                            rib.serverConfiguration.getMultipart(),
                            rib.getIoExecutor(),
                            (Flux<PartData>) withFlowControl(subject.asFlux(), data));
                    }
                }

                Optional<?> converted = rib.conversionService.convert(part, typeVariable);

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

            if (!executed) {
                String argumentName = argument.getName();
                if (!routeMatch.isSatisfied(argumentName)) {
                    Object fulfillParamter = value.get();
                    routeMatch = routeMatch.fulfill(Collections.singletonMap(argumentName, fulfillParamter));
                    // we need to release the data here. However, if the route argument is a
                    // ByteBuffer, we need to retain the data until the route is executed. Adding
                    // the data to the request ensures it is cleaned up after the route completes.
                    if (!alwaysAddContent && fulfillParamter instanceof ByteBufHolder) {
                        request.addContent((ByteBufHolder) fulfillParamter);
                    }
                }
                if (isPublisher && chunkedProcessing) {
                    //accounting for the previous request
                    request(1);
                }
                if (routeMatch.isExecutable() || data instanceof LastHttpContent) {
                    executed = true;
                }
            }

            if (alwaysAddContent && !request.destroyed) {
                request.addContent(data);
            }

            if (!executed || !chunkedProcessing) {
                request(1);
            }

        } else {
            request.addContent(data);
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
