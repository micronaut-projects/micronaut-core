package io.micronaut.management.endpoint

import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder

import java.security.Principal

abstract class BasePrincipalBinder implements TypedRequestArgumentBinder<Principal> {

    abstract String overriddenName();

    @Override
    Argument<Principal> argumentType() {
        return Argument.of(Principal)
    }

    @Override
    BindingResult<Principal> bind(ArgumentConversionContext<Principal> context, HttpRequest<?> source) {
        return new BindingResult<Principal>() {
            @Override
            Optional<Principal> getValue() {
                Optional.of(new Principal() {

                    @Override
                    String getName() {
                        return overriddenName()
                    }
                })
            }
        }
    }
}
