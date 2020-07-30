package io.micronaut.core.bind;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.http.HttpRequest;


public class TestValueAnnotationBinder extends AbstractAnnotatedArgumentBinder<TestValue, T, HttpRequest<?>> implements AnnotatedRequestArgumentBinder<TestValue, T>{

    /**
     * Constructor.
     *
     * @param conversionService conversionService
     */
    protected TestValueAnnotationBinder(ConversionService<?> conversionService)  {
        super(conversionService);
    }

    /**
     * @return The annotation type.
     */
    @Override
    public Class<TestValue> getAnnotationType() { return TestValue.class; }

    /**
     * Bind the given argument from the given source.
     *
     * @param argument The {@link ArgumentConversionContext}
     * @param source  The source
     * @return An {@link Optional} of the value. If no binding was possible {@link Optional#empty()}
     */
    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> argument, TestHttpRequest<?> source) {
        ConvertibleValues<String> parameters = source.getStringValue();
        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        String parameterName = annotationMetadata.stringValue(TestValue.class)
                .orElse(argument.getArgument().getName());
        return doBind(argument, parameters, parameterName);
    }
}
