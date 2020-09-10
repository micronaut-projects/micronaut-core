package io.micronaut.docs.http.client.bind.annotation

//tag::clazz[]
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.util.StringUtils
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.bind.AnnotatedClientArgumentRequestBinder
import io.micronaut.http.client.bind.ClientRequestUriContext
import javax.inject.Singleton

@Singleton
class MetadataClientArgumentBinder : AnnotatedClientArgumentRequestBinder<Metadata> {

    override fun getAnnotationType(): Class<Metadata> {
        return Metadata::class.java
    }

    override fun bind(context: ArgumentConversionContext<Any>,
                      uriContext: ClientRequestUriContext,
                      value: Any,
                      request: MutableHttpRequest<*>) {
        if (value is Map<*, *>) {
            for ((key1, value1) in value) {
                val key = NameUtils.hyphenate(StringUtils.capitalize(key1.toString()), false)
                request.header("X-Metadata-$key", value1.toString())
            }
        }
    }
}
//end::clazz[]
