package io.micronaut.core.bind;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.DefaultConversionService;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class TestValueJavaAnnotationBinderTest {
    ConversionService<?> conversionService;
    HttpRequest webRequest;
    @Inject
    TestValueJavaAnnotationBinder binder;

    @BeforeEach
    public void setup() throws Exception {
        conversionService = new DefaultConversionService();
        binder = new TestValueJavaAnnotationBinder(conversionService);
        webRequest = mock(HttpRequest.class);
    }

    @Test
    public void bindingResult() throws Exception {
        ConvertibleMultiValues values = new ConvertibleMultiValues() {
            @Override
            public List getAll(CharSequence name) {
                ArrayList<String> names = new ArrayList<>( Arrays.asList("one", "two", "three"));
                return names;
            }

            @Nullable
            @Override
            public Object get(CharSequence name) {
                return name;
            }

            @Override
            public Set<String> names() {
                Set<String> names = new HashSet<>(Arrays.asList("one", "two", "three"));
                return names;
            }

            @Override
            public Collection values() {
                Collection<String> values = new ArrayList<>( Arrays.asList("one", "two", "three"));
                return values;
            }

            @Override
            public Optional get(CharSequence name, ArgumentConversionContext conversionContext) {

                return Optional.empty();
            }
        };
        when(webRequest.getAttributes()).thenReturn((MutableConvertibleValues) values.get(webRequest.getMethodName()));
        ATest aTest = new ATest();
        aTest.setName("new Name");
        aTest.setValue("new Value");
        ArgumentConversionContext<ATest> context = new ArgumentConversionContext<ATest>() {
            @Override
            public Argument<ATest> getArgument() {
                return Argument.of(ATest.class);
            }
        };

        Object actual = binder.bind(context,webRequest);
        assertThat(context.getArgument()).isSameAs(actual);
    }

}
