package io.micronaut.docs.http.client.bind.method;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.bind.ClientRequestUriContext;
import org.jetbrains.annotations.NotNull;

import jakarta.inject.Singleton;
import java.util.Map;

//tag::clazz[]
import io.micronaut.http.client.bind.AnnotatedClientRequestBinder;

@Singleton // <1>
public class NameAuthorizationBinder implements AnnotatedClientRequestBinder<NameAuthorization> { // <2>
    @NotNull
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
                .ifPresent(name -> uriContext.getQueryParameters().put("name", String.valueOf(name)));

    }
}
//end::clazz[]
