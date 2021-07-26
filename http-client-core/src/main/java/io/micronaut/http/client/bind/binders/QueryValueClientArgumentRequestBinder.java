/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.bind.binders;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.convert.format.FormattingTypeConverter;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.convert.value.ConvertibleMultiValuesMap;
import io.micronaut.core.convert.value.MutableConvertibleMultiValuesMap;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.bind.AnnotatedClientArgumentRequestBinder;
import io.micronaut.http.client.bind.ClientRequestUriContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Implementation of the Binder for {@link QueryValue}
 * The details of implementation can be found in the
 * {@link #bind(ArgumentConversionContext, ClientRequestUriContext, Object, MutableHttpRequest)} bind()} method javadoc.
 *
 * @author Andriy Dmytruk
 * @since 3.0.0
 */
public class QueryValueClientArgumentRequestBinder implements AnnotatedClientArgumentRequestBinder<QueryValue> {
    /**
     * Values separated with commas ",". In case of iterables, the values are converted to {@link String} and joined
     * with comma delimiter. In case of {@link Map} or a POJO {@link Object} the keys and values are alternating and all
     * delimited with commas.
     * <table borer="0">
     *     <tr> <th><b> Type </b></th>      <th><b> Example value </b></th>                     <th><b> Example representation </b></th> </tr>
     *     <tr> <td> Iterable &emsp </td>   <td> param=["Mike", "Adam", "Kate"] </td>           <td> "param=Mike,Adam,Kate" </td></tr>
     *     <tr> <td> Map </td>              <td> param=["name": "Mike", "age": "30"] &emsp</td> <td> "param=name,Mike,age,30" </td> </tr>
     *     <tr> <td> Object </td>           <td> param={name: "Mike", age: 30} </td>            <td> "param=name,Mike,age,30" </td> </tr>
     * </table>
     * Note that ambiguity may arise when the values contain commas themselves after being converted to String.
     */
    public static final String QUERY_VALUE_FORMAT_CSV = "CSV";
    /**
     * Values separated with spaces " " similarly to CSV being separated with commas.
     */
    public static final String QUERY_VALUE_FORMAT_SSV = "SSV";
    /**
     * Values separated with the pipe "|" symbol similarly to CSV being separated with commas.
     */
    public static final String QUERY_VALUE_FORMAT_PIPES = "PIPES";
    /**
     * Values are repeated with the same parameter name for {@link Iterable}, while {@link Map} and POJO {@link Object}
     * would be expanded with its property names.
     * <table border="1">
     *     <tr> <th><b> Type </b></th>      <th><b> Example value </b></th>                        <th><b> Example representation </b></th> </tr>
     *     <tr> <td> Iterable &emsp </td>   <td> param=["Mike", "Adam", "Kate"] </td>              <td> "param=Mike&param=Adam&param=Kate </td></tr>
     *     <tr> <td> Map </td>              <td> param=["name": "Mike", "age": "30"] &emsp </td>   <td> "name=Mike&age=30" </td> </tr>
     *     <tr> <td> Object </td>           <td> param={name: "Mike", age: 30} </td>               <td> "name=Mike&age=30" </td> </tr>
     * </table>
     */
    public static final String QUERY_VALUE_FORMAT_MULTI = "MULTI";
    /**
     * Values are put in the representation with property name for {@link Map} and POJO {@link Object} in square
     * after the original parameter name.
     * <table border="1">
     *     <tr> <th><b> Type </b></th>      <th><b> Example value </b></th>                        <th><b> Example representation </b></th> </tr>
     *     <tr> <td> Iterable &emsp </td>   <td> param=["Mike", "Adam", "Kate"] </td>              <td> "param[0]=Mike&param[1]=Adam&param[2]=Kate </td></tr>
     *     <tr> <td> Map </td>              <td> param=["name": "Mike", "age": "30"] &emsp </td>   <td> "param[name]=Mike&param[age]=30" </td> </tr>
     *     <tr> <td> Object </td>           <td> param={name: "Mike", age: 30} </td>               <td> "param[name]=Mike&param[age]=30" </td> </tr>
     * </table>
     */
    public static final String QUERY_VALUE_FORMAT_DEEP_OBJECT = "DEEP_OBJECT";

    private static final String CSV_DELIMITER = ",";
    private static final String SSV_DELIMITER = " ";
    private static final String PIPES_DELIMITER = "|";

    private final ConversionService<?> conversionService;

    public QueryValueClientArgumentRequestBinder(ConversionService<?> conversionService) {
        this.conversionService = conversionService;

        // Add the converters
        conversionService.addConverter(Iterable.class, ConvertibleMultiValues.class, new IterableConverter());
        conversionService.addConverter(Map.class, ConvertibleMultiValues.class, new MapConverter());
        conversionService.addConverter(Object.class, ConvertibleMultiValues.class, new ObjectConverter());
    }

    @Override
    @NonNull
    public Class<QueryValue> getAnnotationType() {
        return QueryValue.class;
    }

    /**
     * If value can be converted to ConvertibleMultiValues, then use it and add it to the uriContext.queryParameters.
     * Otherwise if the {@link Format} annotation is present, it is converted to {@link String}. If none of these
     * are satisfied, the{@link io.micronaut.http.uri.UriTemplate} decides what to do with the given value which
     * is supplied as an Object (it is added to uriContext.pathParameter).
     *
     * <br> By default value is converted to ConvertibleMultiValues when the {@link Format} annotation is present and has
     * one of the defined above formats. Otherwise empty optional is returned.
     *
     * <br> The default {@link io.micronaut.http.uri.UriTemplate} will convert the value to String and to parameters.
     * Optionally, the value can be formatted if the path template states so.
     */
    @Override
    public void bind(
            @NonNull ArgumentConversionContext<Object> context,
            @NonNull ClientRequestUriContext uriContext,
            @NonNull Object value,
            @NonNull MutableHttpRequest<?> request
    ) {
        ArgumentConversionContext<ConvertibleMultiValues> conversionContext = ConversionContext.of(
                Argument.of(ConvertibleMultiValues.class, context.getArgument().getName(), context.getAnnotationMetadata()));
        Optional<ConvertibleMultiValues> convertedValue = conversionService.convert(value, conversionContext);

        if (convertedValue.isPresent()) {
            // TODO How should I check if it is truely a List of Strings?
            ConvertibleMultiValues<String> multiValues;
            try {
                // noinspection unchecked
                multiValues = convertedValue.get();
            } catch (Exception e) {
                multiValues = new ConvertibleMultiValuesMap<>();
            }
            Map<String, List<String>> queryParameters = uriContext.getQueryParameters();

            // Add all the parameters
            multiValues.forEach((k, v) -> {
                if (queryParameters.containsKey(k)) {
                    queryParameters.get(k).addAll(v);
                } else {
                    queryParameters.put(k, v);
                }
            });
        } else {
            Argument<Object> argument = context.getArgument();
            String name = argument.getAnnotationMetadata()
                    .getValue(Bindable.class, String.class).orElse(argument.getName());

            if (context.getAnnotationMetadata().hasStereotype(Format.class)) {
                conversionService.convert(value, ConversionContext.STRING.with(context.getAnnotationMetadata()))
                        .ifPresent(v -> uriContext.setPathParameter(name, v));
            } else {
                uriContext.setPathParameter(name, value);
            }
        }
    }

    /**
     * Join strings given a delimiter.
     * @param strings strings to join
     * @param delimiter the delimiter
     * @return joined string
     */
    private String joinStrings(Iterable<String> strings, String delimiter) {
        if (strings == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        boolean first = true;
        for (String value: strings) {
            if (value != null) {
                if (!first) {
                    builder.append(delimiter);
                } else {
                    first = false;
                }
                builder.append(value);
            }
        }

        return builder.toString();
    }

    /**
     * An abstract class to convert to ConvertibleMultiValues.
     * @param <T> The class from which to convert
     */
    public abstract class AbstractMultiValuesConverter<T> implements FormattingTypeConverter<T, ConvertibleMultiValues, Format> {
        @Override
        public Optional<ConvertibleMultiValues> convert(
                T object,
                Class<ConvertibleMultiValues> targetType,
                ConversionContext conversionContext
        ) {
            if (!targetType.isAssignableFrom(MutableConvertibleMultiValuesMap.class) ||
                    !(conversionContext instanceof ArgumentConversionContext)) {
                return Optional.empty();
            }
            // noinspection unchecked
            ArgumentConversionContext<Object> context = (ArgumentConversionContext<Object>) conversionContext;

            String format = conversionContext.getAnnotationMetadata().getValue(Format.class, String.class).orElse(null);
            if (format == null) {
                return Optional.empty();
            }

            String name = conversionContext.getAnnotationMetadata().getValue(Bindable.class, String.class)
                    .orElse(context.getArgument().getName());

            MutableConvertibleMultiValuesMap<String> parameters = new MutableConvertibleMultiValuesMap<>();
            if (object == null) {
                return Optional.of(parameters);
            }

            switch (format) {
                case QUERY_VALUE_FORMAT_CSV:
                    addSeparatedValues(context, name, object, parameters, CSV_DELIMITER);
                    break;
                case QUERY_VALUE_FORMAT_SSV:
                    addSeparatedValues(context, name, object, parameters, SSV_DELIMITER);
                    break;
                case QUERY_VALUE_FORMAT_PIPES:
                    addSeparatedValues(context, name, object, parameters, PIPES_DELIMITER);
                    break;
                case QUERY_VALUE_FORMAT_MULTI:
                    addMutliValues(context, name, object, parameters);
                    break;
                case QUERY_VALUE_FORMAT_DEEP_OBJECT:
                    addDeepObjectValues(context, name, object, parameters);
                    break;
                default:
                    return Optional.empty();
            }

            return Optional.of(parameters);
        }

        @Override
        public Class<Format> annotationType() {
            return Format.class;
        }

        protected abstract void addSeparatedValues(ArgumentConversionContext<Object> context, String name,
                                                   T object, MutableConvertibleMultiValuesMap<String> parameters, String delimiter);

        protected abstract void addMutliValues(ArgumentConversionContext<Object> context, String name,
                T object, MutableConvertibleMultiValuesMap<String> parameters);

        protected abstract void addDeepObjectValues(ArgumentConversionContext<Object> context, String name,
                T object, MutableConvertibleMultiValuesMap<String> parameters);
    }

    /**
     * A converter from {@link Iterable} to {@link ConvertibleMultiValues}.
     */
    public class IterableConverter extends AbstractMultiValuesConverter<Iterable> {
        private void processValues(ArgumentConversionContext<Object> context, Iterable object, Consumer<String> consumer) {
            ArgumentConversionContext<String> conversionContext = ConversionContext.STRING.with(
                    context.getFirstTypeVariable().map(Argument::getAnnotationMetadata).orElse(AnnotationMetadata.EMPTY_METADATA));
            for (Object value: object) {
                conversionService.convert(value, conversionContext).ifPresent(v -> consumer.accept(v));
            }
        }

        @Override
        protected void addSeparatedValues(ArgumentConversionContext<Object> context, String name,
                Iterable object, MutableConvertibleMultiValuesMap<String> parameters, String delimiter) {
            List<String> strings = new ArrayList<>();
            processValues(context, object, v -> strings.add(v));
            parameters.add(name, joinStrings(strings, delimiter));
        }

        @Override
        protected void addMutliValues(ArgumentConversionContext<Object> context, String name,
                Iterable object, MutableConvertibleMultiValuesMap<String> parameters) {
            processValues(context, object, v -> parameters.add(name, v));
        }

        @Override
        protected void addDeepObjectValues(ArgumentConversionContext<Object> context, String name,
                Iterable object, MutableConvertibleMultiValuesMap<String> parameters) {
            ArgumentConversionContext<String> conversionContext = ConversionContext.STRING.with(
                    context.getFirstTypeVariable().map(Argument::getAnnotationMetadata).orElse(AnnotationMetadata.EMPTY_METADATA));

            int i = 0;
            for (Object value: object) {
                String stringValue = conversionService.convert(value, conversionContext).orElse("");
                parameters.add(name + "[" + i + "]", stringValue);
                i++;
            }
        }
    }

    /**
     * A converter from {@link Map} to {@link ConvertibleMultiValues}.
     */
    public class MapConverter extends AbstractMultiValuesConverter<Map> {
        private void processValues(ArgumentConversionContext<Object> context, Map object, BiConsumer<String, String> consumer) {
            // Build conversion context based on annotation metadata for both key and value
            Argument<?>[] typeParameters = context.getTypeParameters();
            ArgumentConversionContext<String> keyConversionContext = ConversionContext.STRING.with(
                    typeParameters.length > 0 ? typeParameters[0].getAnnotationMetadata() : AnnotationMetadata.EMPTY_METADATA);
            ArgumentConversionContext<String> valueConversionContext = ConversionContext.STRING.with(
                    typeParameters.length > 1 ? typeParameters[1].getAnnotationMetadata() : AnnotationMetadata.EMPTY_METADATA);

            // Collect all the key value pairs
            List<String> values = new ArrayList<>();
            // noinspection unchecked
            for (Map.Entry<Object, Object> value: ((Map<Object, Object>) object).entrySet()) {
                conversionService.convert(value.getValue(), valueConversionContext).ifPresent(v -> {
                    conversionService.convert(value.getKey(), keyConversionContext).ifPresent(k -> {
                        consumer.accept(k, v);
                    });
                });
            }
        }

        @Override
        protected void addSeparatedValues(ArgumentConversionContext<Object> context, String name,
               Map object, MutableConvertibleMultiValuesMap<String> parameters, String delimiter) {
            List<String> values = new ArrayList<>();
            processValues(context, object, (k, v) -> {
                values.add(k);
                values.add(v);
            });
            parameters.add(name, joinStrings(values, delimiter));
        }

        @Override
        protected void addMutliValues(ArgumentConversionContext<Object> context, String name,
                Map object, MutableConvertibleMultiValuesMap<String> parameters) {
            processValues(context, object, parameters::add);
        }

        @Override
        protected void addDeepObjectValues(ArgumentConversionContext<Object> context, String name,
                Map object, MutableConvertibleMultiValuesMap<String> parameters) {
           processValues(context, object, (k, v) -> parameters.add(name + "[" + k + "]", v));
        }
    }

    /**
     * A converter from generic {@link Object} to {@link ConvertibleMultiValues}.
     */
    public class ObjectConverter extends AbstractMultiValuesConverter<Object> {
        private void processValues(Object object, BiConsumer<String, String> consumer) {
            BeanWrapper<Object> beanWrapper = BeanWrapper.getWrapper(object);

            for (BeanProperty<Object, Object> property: beanWrapper.getBeanProperties()) {
                String key = property.getValue(Bindable.class, String.class).orElse(property.getName());
                ArgumentConversionContext<String> conversionContext =
                        ConversionContext.STRING.with(property.getAnnotationMetadata());
                conversionService.convert(property.get(object), conversionContext).ifPresent(value -> {
                    consumer.accept(key, value);
                });
            }
        }

        @Override
        protected void addSeparatedValues(ArgumentConversionContext<Object> context, String name,
                Object object, MutableConvertibleMultiValuesMap<String> parameters, String delimiter) {
            List<String> values = new ArrayList<>();
            processValues(object, (k, v) -> {
                values.add(k);
                values.add(v);
            });
            parameters.add(name, joinStrings(values, delimiter));
        }

        @Override
        protected void addMutliValues(ArgumentConversionContext<Object> context, String name,
                 Object object, MutableConvertibleMultiValuesMap<String> parameters) {
            processValues(object, parameters::add);
        }

        @Override
        protected void addDeepObjectValues(ArgumentConversionContext<Object> context, String name,
                Object object, MutableConvertibleMultiValuesMap<String> parameters) {
            processValues(object, (k, v) -> parameters.add(name + "[" + k + "]", v));
        }
    }
}
