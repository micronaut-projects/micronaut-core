package io.micronaut.http.server.netty.binders;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.HttpContentProcessorResolver;
import io.micronaut.http.server.netty.multipart.MultipartBodyArgumentBinder;
import io.reactivex.Flowable;

import javax.inject.Singleton;

/**
 * A binder registrar that requests Netty related binders.
 *
 * @author graemerocher
 * @since 2.0.0
 */
@Singleton
@Internal
@Requires(classes = Flowable.class)
class NettyBinderRegistrar implements BeanCreatedEventListener<RequestBinderRegistry> {
    private final ConversionService<?> conversionService;
    private final HttpContentProcessorResolver httpContentProcessorResolver;
    private final BeanLocator beanLocator;
    private final HttpServerConfiguration httpServerConfiguration;

    /**
     * Default constructor.
     *
     * @param conversionService            The conversion service
     * @param httpContentProcessorResolver The processor resolver
     * @param beanLocator                  The bean locator
     * @param httpServerConfiguration      The server config
     */
    NettyBinderRegistrar(
            ConversionService<?> conversionService,
            HttpContentProcessorResolver httpContentProcessorResolver,
            BeanLocator beanLocator,
            HttpServerConfiguration httpServerConfiguration) {
        this.conversionService = conversionService;
        this.httpContentProcessorResolver = httpContentProcessorResolver;
        this.beanLocator = beanLocator;
        this.httpServerConfiguration = httpServerConfiguration;
    }

    @Override
    public RequestBinderRegistry onCreated(BeanCreatedEvent<RequestBinderRegistry> event) {
        RequestBinderRegistry registry = event.getBean();
        registry.addRequestArgumentBinder(
                new BasicAuthArgumentBinder()
        );
        registry.addRequestArgumentBinder(new MaybeBodyBinder(
                conversionService,
                httpContentProcessorResolver
        ));
        registry.addRequestArgumentBinder(new ObservableBodyBinder(
                conversionService,
                httpContentProcessorResolver
        ));
        registry.addRequestArgumentBinder(new PublisherBodyBinder(
                conversionService,
                httpContentProcessorResolver
        ));
        registry.addRequestArgumentBinder(new SingleBodyBinder(
                conversionService,
                httpContentProcessorResolver
        ));
        registry.addRequestArgumentBinder(new MultipartBodyArgumentBinder(
                beanLocator,
                httpServerConfiguration
        ));
        return registry;
    }
}
