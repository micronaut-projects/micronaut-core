package io.micronaut.docs.http.client.bind.annotation

//tag::clazz[]
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.util.StringUtils
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.bind.AnnotatedClientArgumentRequestBinder
import org.jetbrains.annotations.NotNull

import javax.inject.Singleton

@Singleton
class MetadataClientArgumentBinder implements AnnotatedClientArgumentRequestBinder<Metadata> {

    Class<Metadata> annotationType = Metadata

    @Override
    void bind(@NotNull ArgumentConversionContext<Object> context, @NotNull Object value, @NotNull MutableHttpRequest<?> request) {
        if (value instanceof Map) {
            for (def entry: ((Map) value).entrySet()) {
                String key = NameUtils.hyphenate(StringUtils.capitalize(entry.key.toString()), false)
                request.header("X-Metadata-" + key, entry.value.toString())
            }
        }
    }
}
//end::clazz[]
