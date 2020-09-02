package io.micronaut.docs.http.client.bind.type;

//tag::clazz[]
import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.bind.TypedClientArgumentRequestBinder;

import javax.inject.Singleton;

@Singleton
public class MetadataClientArgumentBinder implements TypedClientArgumentRequestBinder<Metadata> {

    @Override
    @NonNull
    public Argument<Metadata> argumentType() {
        return Argument.of(Metadata.class);
    }

    @Override
    public void bind(@NonNull ArgumentConversionContext<Metadata> context, @NonNull Metadata value, @NonNull MutableHttpRequest<?> request) {
        request.header("X-Metadata-Version", value.getVersion().toString());
        request.header("X-Metadata-Deployment-Id", value.getDeploymentId().toString());
    }
}
//end::clazz[]
