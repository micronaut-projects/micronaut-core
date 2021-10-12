package io.micronaut.docs.http.client.bind.type

//tag::clazz[]
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.bind.ClientRequestUriContext
import io.micronaut.http.client.bind.TypedClientArgumentRequestBinder

import jakarta.inject.Singleton

@Singleton
class MetadataClientArgumentBinder implements TypedClientArgumentRequestBinder<Metadata> {

    @Override
    @NonNull
    Argument<Metadata> argumentType() {
        Argument.of(Metadata)
    }

    @Override
    void bind(@NonNull ArgumentConversionContext<Metadata> context,
              @NonNull ClientRequestUriContext uriContext,
              @NonNull Metadata value,
              @NonNull MutableHttpRequest<?> request) {
        request.header("X-Metadata-Version", value.version.toString())
        request.header("X-Metadata-Deployment-Id", value.deploymentId.toString())
    }
}
//end::clazz[]
