package io.micronaut.core.bind;

<<<<<<< HEAD
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.bind.annotation.AbstractAnnotatedArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;

=======
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.filters.SecurityFilter;
import java.util.Map;
>>>>>>> 3c2dd527111cc27b75929d7ae092ba7f19bee707
import java.util.Optional;


/**
 * The type Test value annotation binder.
 */
<<<<<<< HEAD
public class TestValueAnnotationBinder extends AbstractAnnotatedArgumentBinder<TestValue, ATest, HttpRequest<?>> implements AnnotatedRequestArgumentBinder<TestValue, ATest> {
=======
public class TestValueAnnotationBinder implements AnnotatedRequestArgumentBinder<TestValue, Object> {

    private final ConversionService<?> conversionService;
>>>>>>> 3c2dd527111cc27b75929d7ae092ba7f19bee707

    /**
     * Instantiates a new Test value annotation binder.
     *
     * @param conversionService the conversion service
     */
    public TestValueAnnotationBinder(ConversionService<?> conversionService) {
<<<<<<< HEAD
        super(conversionService);
=======
        this.conversionService = conversionService;
>>>>>>> 3c2dd527111cc27b75929d7ae092ba7f19bee707
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
<<<<<<< HEAD
    public BindingResult<ATest> bind(ArgumentConversionContext<ATest> context, HttpRequest<?> source) {
        ConvertibleValues<Object> parameters = source.getAttributes();
        AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();
        String parameterName = annotationMetadata.stringValue(TestValue.class)
                .orElse(context.getArgument().getName());
        return doBind(context, parameters, parameterName);

=======
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, HttpRequest<?> request) {
        if (request.getAttributes().contains(OncePerRequestHttpServerFilter.getKey(SecurityFilter.class))) {
            final Optional<Authentication> authentication = request.getUserPrincipal(Authentication.class);
            if (authentication.isPresent()) {
                Map<String, Object> roles = (Map<String, Object>) authentication.get().getAttributes().get("roles");
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
>>>>>>> 3c2dd527111cc27b75929d7ae092ba7f19bee707
    }
}