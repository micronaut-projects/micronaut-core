package org.particleframework.http.server.netty.binders;

import com.typesafe.netty.http.StreamedHttpRequest;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.MediaType;
import org.particleframework.http.binding.binders.request.DefaultBodyAnnotationBinder;
import org.particleframework.http.binding.binders.request.TypedRequestArgumentBinder;
import org.particleframework.http.server.netty.JsonContentSubscriber;
import org.particleframework.http.server.netty.NettyHttpRequest;
import org.particleframework.inject.Argument;

//import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
//@Singleton
public class CompletableFutureBodyBinder extends DefaultBodyAnnotationBinder<CompletableFuture>
        implements TypedRequestArgumentBinder<CompletableFuture> {

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

                // TODO / WIP
//                streamedHttpRequest.subscribe(new JsonContentSubscriber());

                return Optional.empty();
            } else {

                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }
}
