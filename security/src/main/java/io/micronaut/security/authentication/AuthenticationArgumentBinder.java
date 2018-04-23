package io.micronaut.security.authentication;

import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.server.binding.binders.TypedRequestArgumentBinder;
import io.micronaut.security.filters.SecurityFilter;

import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class AuthenticationArgumentBinder implements TypedRequestArgumentBinder<Authentication> {

    @Override
    public Argument<Authentication> argumentType() {
        return Argument.of(Authentication.class);
    }

    @Override
    public BindingResult<Authentication> bind(ArgumentConversionContext<Authentication> context, HttpRequest<?> source) {
        if (source.getAttributes().contains(OncePerRequestHttpServerFilter.getKey(SecurityFilter.class))) {
            MutableConvertibleValues<Object> attrs = source.getAttributes();
            Optional<Authentication> existing = attrs.get(SecurityFilter.AUTHENTICATION, Authentication.class);
            if (existing.isPresent()) {
                return () -> existing;
            }
        }

        return ArgumentBinder.BindingResult.EMPTY;
    }
}
