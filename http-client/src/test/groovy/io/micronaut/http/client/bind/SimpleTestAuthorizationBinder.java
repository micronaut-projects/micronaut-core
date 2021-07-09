package io.micronaut.http.client.bind;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.http.MutableHttpRequest;
import jakarta.inject.Singleton;

@Singleton
public class SimpleTestAuthorizationBinder implements AnnotatedClientRequestBinder<SimpleTestAuthorization> {
    @Override
    public void bind(MethodInvocationContext<Object, Object> context, ClientRequestUriContext uriContext, MutableHttpRequest<?> request) {
        uriContext.addQueryParameter("name", "admin");
    }

    @Override
    public Class<SimpleTestAuthorization> getAnnotationType() {
        return SimpleTestAuthorization.class;
    }
}