package org.particleframework.http.server.netty.binders;

import com.fasterxml.jackson.databind.JsonNode;
import com.typesafe.netty.http.StreamedHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.MediaType;
import org.particleframework.http.binding.binders.request.DefaultBodyAnnotationBinder;
import org.particleframework.http.binding.binders.request.NonBlockingBodyArgumentBinder;
import org.particleframework.http.server.netty.HttpContentSubscriber;
import org.particleframework.http.server.netty.JsonContentSubscriber;
import org.particleframework.http.server.netty.NettyHttpRequest;
import org.particleframework.inject.Argument;
import org.reactivestreams.Subscriber;

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

    public CompletableFutureBodyBinder(ConversionService conversionService) {
        super(conversionService);
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
                Subscriber<HttpContent> subscriber;
                if(contentType != null && contentType.getExtension().equals(MediaType.APPLICATION_JSON_TYPE.getExtension())) {

                    subscriber = new JsonContentSubscriber(nettyHttpRequest) {
                        @Override
                        protected void onComplete(JsonNode jsonNode) {

                            if(!future.isCompletedExceptionally()) {
                                Class[] genericTypes = argument.getGenericTypes();
                                if(genericTypes != null && genericTypes.length > 0) {
                                    Class targetType = genericTypes[0];
                                    Optional converted = conversionService.convert(jsonNode, targetType);
                                    if(converted.isPresent()) {
                                        future.complete(converted.get());
                                    }
                                    else {
                                        future.completeExceptionally(new IllegalArgumentException("Cannot bind JSON to argument type: " + targetType.getName()));
                                    }
                                }
                                else {
                                    future.complete(jsonNode);
                                }
                            }
                        }
                    };
                }
                else {
                    subscriber = new HttpContentSubscriber(nettyHttpRequest) {
                        @Override
                        public void onComplete() {
                            if(!future.isCompletedExceptionally()) {
                                Class[] genericTypes = argument.getGenericTypes();
                                if(genericTypes != null && genericTypes.length > 0) {
                                    Class targetType = genericTypes[0];
                                    Optional converted = conversionService.convert(nettyHttpRequest.getBody(), targetType);
                                    if(converted.isPresent()) {
                                        future.complete(converted.get());
                                    }
                                    else {
                                        future.completeExceptionally(new IllegalArgumentException("Cannot bind JSON to argument type: " + targetType.getName()));
                                    }
                                }
                                else {
                                    future.complete(nettyHttpRequest.getBody());
                                }
                            }
                        }
                    };
                }
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
