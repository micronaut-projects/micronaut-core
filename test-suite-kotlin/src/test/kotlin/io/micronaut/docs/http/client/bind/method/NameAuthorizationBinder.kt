package io.micronaut.docs.http.client.bind.method;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.bind.ClientRequestUriContext;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;

//tag::clazz[]
import io.micronaut.http.client.bind.AnnotatedClientRequestBinder

@Singleton // <1>
class NameAuthorizationBinder: AnnotatedClientRequestBinder<NameAuthorization> { // <2>
    @NotNull
    override fun getAnnotationType(): Class<NameAuthorization> {
        return NameAuthorization::class.java
    }

    override fun bind( // <3>
            @NonNull context: MethodInvocationContext<Any, Any>,
            @NonNull uriContext: ClientRequestUriContext,
            @NonNull request: MutableHttpRequest<*>
    ) {
        context.getValue(NameAuthorization::class.java, "name")
                .ifPresent { name -> uriContext.addQueryParameter("name", name.toString()) }

    }
}
//end::clazz[]
