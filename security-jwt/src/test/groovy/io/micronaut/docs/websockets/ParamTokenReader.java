package io.micronaut.docs.websockets;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.token.reader.TokenReader;

import javax.inject.Singleton;
import java.util.Optional;

@Requires(property = "spec.name", value = "websockets")
@Singleton
class ParamTokenReader implements TokenReader {

    public static final String PARAM_NAME = "token";

    @Override
    public Optional<String> findToken(HttpRequest<?> request) {
        return Optional.ofNullable(request.getParameters().get(PARAM_NAME));
    }
}
