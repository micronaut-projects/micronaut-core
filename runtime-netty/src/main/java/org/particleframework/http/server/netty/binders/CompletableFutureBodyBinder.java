package org.particleframework.http.server.netty.binders;

import com.typesafe.netty.http.StreamedHttpRequest;
import org.particleframework.context.BeanLocator;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.MediaType;
import org.particleframework.http.binding.binders.request.DefaultBodyAnnotationBinder;
import org.particleframework.http.binding.binders.request.NonBlockingBodyArgumentBinder;
import org.particleframework.http.server.netty.DefaultHttpContentSubscriber;
import org.particleframework.http.server.netty.HttpContentSubscriber;
import org.particleframework.http.server.netty.HttpContentSubscriberFactory;
import org.particleframework.http.server.netty.NettyHttpRequest;
import org.particleframework.core.type.Argument;
import org.particleframework.web.router.qualifier.ConsumesMediaTypeQualifier;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link NonBlockingBodyArgumentBinder} that handles {@link CompletableFuture} instances
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class CompletableFutureBodyBinder extends DefaultBodyAnnotationBinder<CompletableFuture>
        implements NonBlockingBodyArgumentBinder<CompletableFuture> {

    private final BeanLocator beanLocator;

    public CompletableFutureBodyBinder(BeanLocator beanLocator, ConversionService conversionService) {
        super(conversionService);
        this.beanLocator = beanLocator;
    }

    @Override
    public Class<CompletableFuture> argumentType() {
        return CompletableFuture.class;
    }

    @Override
    public Optional<CompletableFuture> bind(Argument<CompletableFuture> argument, HttpRequest source) {
        if (source instanceof NettyHttpRequest) {
            NettyHttpRequest nettyHttpRequest = (NettyHttpRequest) source;
            io.netty.handler.codec.http.HttpRequest nativeRequest = ((NettyHttpRequest) source).getNativeRequest();
            if (nativeRequest instanceof StreamedHttpRequest) {

                MediaType contentType = source.getContentType();
                CompletableFuture future = new CompletableFuture();
                StreamedHttpRequest streamedHttpRequest = (StreamedHttpRequest) nativeRequest;
                HttpContentSubscriber subscriber;
                if (contentType != null) {

                    Optional<HttpContentSubscriberFactory> subscriberBean = beanLocator.findBean(HttpContentSubscriberFactory.class,
                            new ConsumesMediaTypeQualifier<>(contentType));


                    subscriber = subscriberBean.map(factory -> factory.build(nettyHttpRequest))
                                               .orElse(new DefaultHttpContentSubscriber(nettyHttpRequest));
                } else {
                    subscriber = new DefaultHttpContentSubscriber(nettyHttpRequest);

                }
                subscriber.onComplete((body) -> {
                            if (!future.isCompletedExceptionally()) {
                                Class[] genericTypes = argument.getGenericTypes();
                                if (genericTypes != null && genericTypes.length > 0) {
                                    Class targetType = genericTypes[0];
                                    Optional converted = conversionService.convert(body, targetType);
                                    if (converted.isPresent()) {
                                        future.complete(converted.get());
                                    } else {
                                        future.completeExceptionally(new IllegalArgumentException("Cannot bind JSON to argument type: " + targetType.getName()));
                                    }
                                } else {
                                    future.complete(body);
                                }
                            }
                        }

                );
                streamedHttpRequest.subscribe(subscriber);

                return Optional.of(future);
            } else {

                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }
}
