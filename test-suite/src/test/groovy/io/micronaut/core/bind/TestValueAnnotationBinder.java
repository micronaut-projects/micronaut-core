package io.micronaut.core.bind;

import io.jaegertracing.internal.utils.Http;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.session.Session;
import io.micronaut.session.annotation.SessionValue;
import io.micronaut.session.http.HttpSessionFilter;

import java.util.Optional;


public class TestValueAnnotationBinder implements AnnotatedRequestArgumentBinder<TestValue, T> {
    /**
     * @return The annotation type.
     */
    @Override
    public Class<TestValue> getAnnotationType() {
        return TestValue.class;
    }

    /**
     * Bind the given argument from the given source.
     *
     * @param context The {@link ArgumentConversionContext}
     * @param source  The source
     * @return An {@link Optional} of the value. If no binding was possible {@link Optional#empty()}
     */
    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        MutableConvertibleValues<T> attrs = source.getAttributes();
        if (!attrs.contains(OncePerRequestHttpServerFilter.getKey(HttpSessionFilter.class))) {
            // the filter hasn't been executed but the argument is not satisfied
            //no inspection unchecked
            return ArgumentBinder.BindingResult.UNSATISFIED;
        }

        Argument<T> argument = context.getArgument();
        String name = context.getAnnotationMetadata().stringValue(TestValue.class).orElse(argument.getName());
        Optional<T> existing = attrs.get(HttpRequest.TEST_ATTRIBUTE, Session.class);
//        if (existing.isPresent()) {
//            String finalName = name;
//            T session = existing.get();
//            return () -> session.get(finalName, context);
//        } else {
//            //noinspection unchecked
//            return ArgumentBinder.BindingResult.EMPTY;
//        }
//    }


//    /**
//     * @return The annotation type.
//     */
//    @Override
//    public Class<TestValue> getAnnotationType() {  }
//
//    /**
//     * Bind the given argument from the given source.
//     *
//     * @param argument The {@link ArgumentConversionContext}
//     * @param source  The source
//     * @return An {@link Optional} of the value. If no binding was possible {@link Optional#empty()}
//     */
//    @Override
//    public BindingResult<T> bind(ArgumentConversionContext<T> argument, TestHttpRequest<?> source) {
//        ConvertibleValues<String> parameters = source.getStringValue();
//        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
//        String parameterName = annotationMetadata.stringValue(TestValue.class)
//                .orElse(argument.getArgument().getName());
//        return doBind(argument, parameters, parameterName);
//    }
}
