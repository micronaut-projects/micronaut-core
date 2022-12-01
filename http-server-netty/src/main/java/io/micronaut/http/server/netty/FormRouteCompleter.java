package io.micronaut.http.server.netty;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
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
    final Collection<Sinks.Many<Object>> downstreamSubscribers = new ArrayList<>();
    final Map<IdentityWrapper, HttpDataReference> dataReferences = new HashMap<>();

    boolean inputCancelled = false;

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

    private <T> Flux<T> withFlowControl(Flux<T> flux, HttpDataReference dataReference) {
        return flux
            .doOnComplete(dataReference::destroy)
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
                    addData(data);
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

    private void addData(HttpData data) {
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
                HttpDataReference dataReference = dataReferences.computeIfAbsent(new IdentityWrapper(data), key -> new HttpDataReference(data));
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
                    dataReference.subject.getAndUpdate(subject -> {
                        if (subject == null) {
                            Sinks.Many<Object> childSubject = makeDownstreamUnicastProcessor();
                            Flux flowable = withFlowControl(childSubject.asFlux(), dataReference);
                            if (streamingFileUpload && data instanceof FileUpload) {
                                namedSubject.tryEmitNext(new NettyStreamingFileUpload(
                                    (FileUpload) data,
                                    rib.serverConfiguration.getMultipart(),
                                    rib.getIoExecutor(),
                                    (Flux<PartData>) flowable));
                            } else {
                                namedSubject.tryEmitNext(flowable);
                            }

                            return childSubject;
                        }
                        return subject;
                    });
                }

                Sinks.Many subject;

                final Sinks.Many<Object> ds = dataReference.subject.get();
                if (ds != null) {
                    subject = ds;
                } else {
                    subject = namedSubject;
                }

                Object part = data;

                if (chunkedProcessing) {
                    MicronautHttpData<?>.Chunk chunk = ((MicronautHttpData<?>) data).pollChunk();
                    part = new NettyPartData(dataReference, chunk::claim);
                }

                if (data instanceof FileUpload &&
                    StreamingFileUpload.class.isAssignableFrom(argument.getType())) {
                    dataReference.upload.getAndUpdate(upload -> {
                        if (upload == null) {
                            return new NettyStreamingFileUpload(
                                (FileUpload) data,
                                rib.serverConfiguration.getMultipart(),
                                rib.getIoExecutor(),
                                (Flux<PartData>) withFlowControl(subject.asFlux(), dataReference));
                        }
                        return upload;
                    });
                }

                Optional<?> converted = rib.conversionService.convert(part, typeVariable);

                converted.ifPresent(subject::tryEmitNext);

                if (data.isCompleted() && chunkedProcessing) {
                    subject.tryEmitComplete();
                }

                value = () -> {
                    StreamingFileUpload upload = dataReference.upload.get();
                    if (upload != null) {
                        return upload;
                    } else {
                        if (dataReference.subject.get() == null) {
                            return withFlowControl(namedSubject.asFlux(), dataReference);
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

    private Sinks.Many<Object> makeDownstreamUnicastProcessor() {
        Sinks.Many<Object> processor = Sinks.many().unicast().onBackpressureBuffer();
        downstreamSubscribers.add(processor);
        return processor;
    }
}
