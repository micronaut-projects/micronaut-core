package io.micronaut.http.server.netty.binders;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.DefaultBodyAnnotationBinder;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.HttpContentProcessorResolver;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.body.ImmediateByteBody;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
@Replaces(DefaultBodyAnnotationBinder.class)
class NettyBodyAnnotationBinder<T> extends DefaultBodyAnnotationBinder<T> {
    private static final CharSequence ATTR_CONVERTIBLE_BODY = "NettyBodyAnnotationBinder.convertibleBody";

    private final HttpContentProcessorResolver httpContentProcessorResolver;
    private final HttpServerConfiguration httpServerConfiguration;

    public NettyBodyAnnotationBinder(ConversionService conversionService, HttpContentProcessorResolver httpContentProcessorResolver, HttpServerConfiguration httpServerConfiguration) {
        super(conversionService);
        this.httpContentProcessorResolver = httpContentProcessorResolver;
        this.httpServerConfiguration = httpServerConfiguration;
    }

    @Override
    public BindingResult<ConvertibleValues<?>> bindFullBodyConvertibleValues(HttpRequest<?> source) {
        if (!(source instanceof NettyHttpRequest<?> nhr)) {
            return super.bindFullBodyConvertibleValues(source);
        }
        Optional<Object> existing = nhr.getAttribute(ATTR_CONVERTIBLE_BODY);
        if (existing.isPresent()) {
            return (BindingResult<ConvertibleValues<?>>) existing.get();
        } else {
            //noinspection unchecked
            BindingResult<ConvertibleValues<?>> result = (BindingResult<ConvertibleValues<?>>) bindFullBody((ArgumentConversionContext<T>) ConversionContext.of(ConvertibleValues.class), source);
            nhr.setAttribute(ATTR_CONVERTIBLE_BODY, result);
            return result;
        }
    }

    @Override
    public BindingResult<T> bindFullBody(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        if (!(source instanceof NettyHttpRequest<?> nhr)) {
            return super.bindFullBody(context, source);
        }
        if (nhr.rootBody() instanceof ImmediateByteBody imm && imm.empty()) {
            return BindingResult.empty();
        }

        ExecutionFlow<ImmediateByteBody> buffered = nhr.rootBody()
            .buffer(nhr.getChannelHandlerContext().alloc());

        return new PendingRequestBindingResult<T>() {
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
            Optional<T> result;

            {
                // NettyRequestLifecycle will "subscribe" to the execution flow added to routeWaitsFor,
                // so we can't subscribe directly ourselves. Instead, use the side effect of a map.
                nhr.addRouteWaitsFor(buffered.map(imm -> {
                    try {
                        //noinspection unchecked
                        result = imm.processSingle(
                                httpContentProcessorResolver.resolve(nhr, context.getArgument()).resultType(context.getArgument()),
                                httpServerConfiguration.getDefaultCharset(),
                                nhr.getChannelHandlerContext().alloc()
                            )
                            .convert(conversionService, context)
                            .map(o -> (T) o.claimForExternal());
                    } catch (Throwable e) {
                        result = Optional.empty();
                    }
                    return null;
                }));
            }

            @SuppressWarnings("OptionalAssignedToNull")
            @Override
            public boolean isPending() {
                return result == null;
            }

            @Override
            public Optional<T> getValue() {
                return result;
            }

            @Override
            public List<ConversionError> getConversionErrors() {
                return context.getLastError().map(List::of).orElseGet(List::of);
            }
        };
    }
}
