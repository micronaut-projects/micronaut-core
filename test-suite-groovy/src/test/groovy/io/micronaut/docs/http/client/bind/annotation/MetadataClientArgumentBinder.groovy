package io.micronaut.docs.http.client.bind.annotation

//tag::clazz[]
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.util.StringUtils
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.bind.AnnotatedClientArgumentRequestBinder
import io.micronaut.http.client.bind.ClientRequestUriContext
import org.jetbrains.annotations.NotNull

import jakarta.inject.Singleton

@Singleton
class MetadataClientArgumentBinder implements AnnotatedClientArgumentRequestBinder<Metadata> {

    final Class<Metadata> annotationType = Metadata

    @Override
    void bind(@NotNull ArgumentConversionContext<Object> context,
              @NonNull ClientRequestUriContext uriContext,
              @NotNull Object value,
              @NotNull MutableHttpRequest<?> request) {
        if (value instanceof Map) {
            for (entry in value.entrySet()) {
                String key = NameUtils.hyphenate(StringUtils.capitalize(entry.key as String), false)
                request.header("X-Metadata-$key", entry.value as String)
            }
        }
    }
}
//end::clazz[]
