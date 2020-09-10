package io.micronaut.upload;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;

import javax.inject.Singleton;
import java.security.Principal;
import java.util.Optional;

@Singleton
public class PrincipalBinder implements TypedRequestArgumentBinder<Principal> {

    @Override
    public Argument<Principal> argumentType() {
        return Argument.of(Principal.class);
    }

    @Override
    public BindingResult<Principal> bind(ArgumentConversionContext<Principal> context, HttpRequest<?> source) {
        if (source.getAttributes().contains(OncePerRequestHttpServerFilter.getKey(PrincipalFilter.class))) {
            final Optional<Principal> existing = source.getUserPrincipal();
            if (existing.isPresent()) {
                return () -> existing;
            } else {
                return BindingResult.EMPTY;
            }
        } else {
            return BindingResult.UNSATISFIED;
        }
    }
}
