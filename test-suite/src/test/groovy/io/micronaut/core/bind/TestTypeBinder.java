package io.micronaut.core.bind;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaderValues;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

public class TestTypeBinder implements TypedRequestArgumentBinder<ATest> {

    @Override
    public boolean supportsSuperTypes() {
        return false;
    }

    @Override
    public Argument<ATest> argumentType() {
        return Argument.of(ATest.class);
    }

    @Override
    public BindingResult<ATest> bind(ArgumentConversionContext<ATest> context, HttpRequest<?> source) {
        final String authorization = source.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(HttpHeaderValues.AUTHORIZATION_PREFIX_BASIC)) {
            String base64Credentials = authorization.substring(6);
            byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(credDecoded, StandardCharsets.UTF_8);
            // credentials = username:password
            final String[] values = credentials.split(":", 2);
            if (values.length == 2) {
                return () -> Optional.of(new ATest(values[0], values[1]);
            }
        }
        return BindingResult.EMPTY;
    }
}
