package io.micronaut.docs.http.client.bind.annotation;

//tag::clazz[]
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.bind.AnnotatedClientArgumentRequestBinder;
import io.micronaut.http.client.bind.ClientRequestUriContext;

import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public class MetadataClientArgumentBinder implements AnnotatedClientArgumentRequestBinder<Metadata> {

    @NonNull
    @Override
    public Class<Metadata> getAnnotationType() {
        return Metadata.class;
    }

    @Override
    public void bind(@NonNull ArgumentConversionContext<Object> context,
                     @NonNull ClientRequestUriContext uriContext,
                     @NonNull Object value,
                     @NonNull MutableHttpRequest<?> request) {
        if (value instanceof Map map) {
            for (Map.Entry<?, ?> entry: map.entrySet()) {
                String key = NameUtils.hyphenate(StringUtils.capitalize(entry.getKey().toString()), false);
                request.header("X-Metadata-" + key, entry.getValue().toString());
            }
        }
    }
}
//end::clazz[]
