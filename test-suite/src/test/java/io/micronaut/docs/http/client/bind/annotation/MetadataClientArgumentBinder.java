package io.micronaut.docs.http.client.bind.annotation;

//tag::clazz[]
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.bind.AnnotatedClientArgumentRequestBinder;
import org.jetbrains.annotations.NotNull;

import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class MetadataClientArgumentBinder implements AnnotatedClientArgumentRequestBinder<Metadata> {

    @NotNull
    @Override
    public Class<Metadata> getAnnotationType() {
        return Metadata.class;
    }


    @Override
    public void bind(@NotNull ArgumentConversionContext<Object> context, @NotNull Object value, @NotNull MutableHttpRequest<?> request) {
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry: ((Map<?, ?>) value).entrySet()) {
                String key = NameUtils.hyphenate(StringUtils.capitalize(entry.getKey().toString()), false);
                request.header("X-Metadata-" + key, entry.getValue().toString());
            }
        }
    }
}
//end::clazz[]
