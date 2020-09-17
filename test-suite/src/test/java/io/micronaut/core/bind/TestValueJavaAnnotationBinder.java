package io.micronaut.core.bind;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.bind.annotation.AbstractAnnotatedArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;

import java.util.Optional;


/**
 * The type Test value annotation binder.
 */
public class TestValueJavaAnnotationBinder extends AbstractAnnotatedArgumentBinder<TestValue, ATest, HttpRequest<?>> implements AnnotatedRequestArgumentBinder<TestValue, ATest> {

    /**
     * Instantiates a new Test value annotation binder.
     *
     * @param conversionService the conversion service
     */
    public TestValueJavaAnnotationBinder(ConversionService<?> conversionService) {
        super(conversionService);
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
    public BindingResult<ATest> bind(ArgumentConversionContext<ATest> context, HttpRequest<?> source) {
        ConvertibleMultiValues values = (ConvertibleMultiValues) source.getAttributes();
        AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();
        String parameterName = annotationMetadata.stringValue(TestValue.class)
                .orElse(context.getArgument().getName());
        return doBind(context, values, parameterName);

    }
}