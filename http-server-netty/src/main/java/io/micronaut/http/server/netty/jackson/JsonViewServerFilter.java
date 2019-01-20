package io.micronaut.http.server.netty.jackson;

import com.fasterxml.jackson.annotation.JsonView;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.jackson.JacksonConfiguration;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Requires(beans = JacksonConfiguration.class)
@Requires(property = "jackson.json-view-enabled")
@Filter("/**")
public class JsonViewServerFilter implements HttpServerFilter {
    private static final Logger LOG = LoggerFactory.getLogger(JsonViewServerFilter.class);

    private JsonViewMediaTypeCodecFactory codecFactory;

    public JsonViewServerFilter(JsonViewMediaTypeCodecFactory codecFactory) {
        this.codecFactory = codecFactory;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        Optional<AnnotationMetadata> routeMatch = request.getAttribute(HttpAttributes.ROUTE_MATCH, AnnotationMetadata.class);
        if (routeMatch.isPresent()) {
            AnnotationMetadata metadata = routeMatch.get();

            Optional<Class> viewClass = metadata.classValue(JsonView.class);
            if (viewClass.isPresent()) {
                MediaTypeCodec codec = codecFactory.createJsonViewCodec(viewClass.get());
                request.setAttribute(HttpAttributes.MEDIA_TYPE_CODEC, codec);
            }
        }

        return chain.proceed(request);
    }
}
