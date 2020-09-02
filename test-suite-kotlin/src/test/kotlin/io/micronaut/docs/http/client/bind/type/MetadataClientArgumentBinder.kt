package io.micronaut.docs.http.client.bind.type

//tag::clazz[]
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.bind.TypedClientArgumentRequestBinder
import javax.inject.Singleton

@Singleton
class MetadataClientArgumentBinder : TypedClientArgumentRequestBinder<Metadata?> {

    override fun argumentType(): Argument<Metadata?> {
        return Argument.of(Metadata::class.java)
    }

    override fun bind(context: ArgumentConversionContext<Metadata?>, value: Metadata, request: MutableHttpRequest<*>) {
        request.header("X-Metadata-Version", value.version.toString())
        request.header("X-Metadata-Deployment-Id", value.deploymentId.toString())
    }
}
//end::clazz[]
