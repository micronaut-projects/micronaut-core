package io.micronaut.core.bind;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.filters.SecurityFilter;

import java.util.Map;
import java.util.Optional;


/**
 * The type Test value annotation binder.
 */
public class TestValueAnnotationBinder implements AnnotatedRequestArgumentBinder<TestValue, Object> {

    private final ConversionService<?> conversionService;

    /**
     * Instantiates a new Test value annotation binder.
     *
     * @param conversionService the conversion service
     */
    public TestValueAnnotationBinder(ConversionService<?> conversionService) {
        this.conversionService = conversionService;
    }

    /**
     * @return The annotation type.
     */
    @Override
    public Class<TestValue> getAnnotationType() {
        return TestValue.class;
    }

    /**
     * Bind the given argument from the given request.
     *
     * @param context The {@link ArgumentConversionContext}
     * @param request  The request
     * @return An {@link Optional} of the value. If no binding was possible {@link Optional#empty()}
     */
    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, HttpRequest<?> request) {
        if (request.getAttributes().contains(OncePerRequestHttpServerFilter.getKey(SecurityFilter.class))) {
            final Optional<Authentication> authentication = request.getUserPrincipal(Authentication.class);
            if (authentication.isPresent()) {
                Map<String, Object> roles = authentication.get().getAttributes().containsKey('roles');
                if (roles.isEmpty()) {
                    return BindingResult.EMPTY; // satisfied
                } else {
                    // get the first role
                    return (BindingResult<Object>) roles.get(0); // satisfied
                }
            } else {
                return BindingResult.EMPTY; // satisfied
            }
        }
        return BindingResult.UNSATISFIED; // try again
    }
}