package io.micronaut.http.form;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.ServerHttpRequest;
import org.reactivestreams.Publisher;

public interface FormCapableHttpRequest<B> extends ServerHttpRequest<B> {
    @NonNull
    Publisher<RawFormField> getRawFormFields() throws IllegalStateException;

    boolean hasFormBody();
}
