package io.micronaut.docs.http.client.bind.method;

import io.micronaut.aop.MethodInvocationContext;
import org.jspecify.annotations.NonNull;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.bind.AnnotatedClientRequestBinder;
import io.micronaut.http.client.bind.ClientRequestUriContext;
import jakarta.inject.Singleton;

//tag::clazz[]

@Singleton // <1>
public class NameAuthorizationBinder implements AnnotatedClientRequestBinder<NameAuthorization> { // <2>
    @NonNull
    @Override
    public Class<NameAuthorization> getAnnotationType() {
        return NameAuthorization.class;
    }

    @Override
    public void bind( // <3>
            @NonNull MethodInvocationContext<Object, Object> context,
            @NonNull ClientRequestUriContext uriContext,
            @NonNull MutableHttpRequest<?> request
    ) {
        context.getValue(NameAuthorization.class)
                .ifPresent(name -> uriContext.addQueryParameter("name", String.valueOf(name)));

    }
}
//end::clazz[]
