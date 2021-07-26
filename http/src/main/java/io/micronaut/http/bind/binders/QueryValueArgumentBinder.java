/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.bind.binders;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.bind.annotation.AbstractAnnotatedArgumentBinder;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.convert.format.FormattingTypeConverter;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchVariable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A binder for binding arguments annotated with @QueryValue.
 *
 * @param <T> The argument type
 * @author James Kleeh
 * @author Andriy Dmytruk
 * @since 2.0.2
 */
public class QueryValueArgumentBinder<T> extends AbstractAnnotatedArgumentBinder<QueryValue, T, HttpRequest<?>> implements AnnotatedRequestArgumentBinder<QueryValue, T> {
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

    private static final Character CSV_DELIMITER = ',';
    private static final Character SSV_DELIMITER = ' ';
    private static final Character PIPES_DELIMITER = '|';

    private final ConversionService<?> conversionService;

    /**
     * Constructor.
     *
     * @param conversionService conversion service
     */
    public QueryValueArgumentBinder(ConversionService<?> conversionService) {
        super(conversionService);
        this.conversionService = conversionService;

        conversionService.addConverter(ConvertibleMultiValues.class, Iterable.class, new IterableConverter());
        conversionService.addConverter(ConvertibleMultiValues.class, Map.class, new MapConverter());
        conversionService.addConverter(ConvertibleMultiValues.class, Object.class, new ObjectConverter());
    }

    @Override
    public Class<QueryValue> getAnnotationType() {
        return QueryValue.class;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        ConvertibleMultiValues<String> parameters = source.getParameters();
        Argument<T> argument = context.getArgument();
        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        boolean hasAnnotation = annotationMetadata.hasAnnotation(QueryValue.class);

        HttpMethod httpMethod = source.getMethod();
        boolean permitsRequestBody = HttpMethod.permitsRequestBody(httpMethod);

        BindingResult<T> result;
        // If we need to bind all request params to command object
        // checks if the variable is defined with modifier char *
        // eg. ?pojo*
        if (hasAnnotation || !permitsRequestBody) {
            // First try converting from the ConvertibleMultiValues type and if conversion is successful, return it.
            // The conversion only starts if there is a Format annotation
            // Otherwise use the given uri template to deduce what to do with the variable
            Optional<T> multiValueConversion;
            if (annotationMetadata.hasAnnotation(Format.class)) {
                multiValueConversion = conversionService.convert(parameters, context);
            } else {
                multiValueConversion = Optional.empty();
            }

            if (multiValueConversion.isPresent()) {
                result = () -> multiValueConversion;
            } else {
                String parameterName = annotationMetadata.stringValue(QueryValue.class).orElse(argument.getName());
                boolean bindAll = source.getAttribute(HttpAttributes.ROUTE_MATCH, UriMatchInfo.class)
                        .map(umi -> {
                            UriMatchVariable uriMatchVariable = umi.getVariableMap().get(parameterName);
                            return uriMatchVariable != null && uriMatchVariable.isExploded();
                        }).orElse(false);

                if (bindAll) {
                    Object value;
                    // Only maps and POJOs will "bindAll", lists work like normal
                    if (Iterable.class.isAssignableFrom(argument.getType())) {
                        value = doResolve(context, parameters, parameterName);
                        if (value == null) {
                            value = Collections.emptyList();
                        }
                    } else {
                        value = parameters.asMap();
                    }
                    result = doConvert(value, context);
                } else {
                    result = doBind(context, parameters, parameterName);
                }
            }
        } else {
            result = BindingResult.EMPTY;
        }
        return result;
    }

    /**
     * A common function for {@link MapConverter} and {@link ObjectConverter}.
     */
    private Map<String, String> getSeparatedMapParameters(
            ConvertibleMultiValues<String> parameters, String name, Optional<String> defaultValue, Character delimiter
    ) {
        List<String> paramValues = parameters.getAll(name);

        if (paramValues.size() == 0 && defaultValue.isPresent()) {
            paramValues.add(defaultValue.get());
        }

        Map<String, String> values = new HashMap<>();
        for (String value: paramValues) {
            List<String> delimited = splitByDelimiter(value, delimiter);
            for (int i = 1; i < delimited.size(); i += 2) {
                values.put(delimited.get(i - 1), delimited.get(i));
            }
        }

        return values;
    }

    /**
     * A common function for {@link MapConverter} and {@link ObjectConverter}.
     */
    private Map<String, String> getMultiMapParameters(
            ConvertibleMultiValues<String> parameters, String name, Optional<String> defaultValue
    ) {
        // Convert to map of strings - if multiple values are present, the first one is taken
        Map values = parameters.asMap().entrySet().stream()
                .filter(v -> !v.getValue().isEmpty())
                .collect(Collectors.toMap(v -> v.getKey(), v -> v.getValue().get(0)));
        return values;
    }

    /**
     * A common function for {@link MapConverter} and {@link ObjectConverter}.
     */
    private Map<String, String> getDeepObjectMapParameters(
            ConvertibleMultiValues<String> parameters, String name, Optional<String> defaultValue
    ) {
        Map<String, List<String>> paramValues = parameters.asMap();
        Map<String, String> values = new HashMap<>();

        // Convert to map of strings - if multiple values are present, only first one is taken
        for (Map.Entry<String, List<String>> param: paramValues.entrySet()) {
            String key = param.getKey();
            if (key.startsWith(name) && key.length() > name.length() &&
                    key.charAt(name.length()) == '[' && key.charAt(key.length() - 1) == ']' &&
                    param.getValue().size() > 0
            ) {
                String mapKey = key.substring(name.length() + 1, key.length() - 1);
                values.put(mapKey, param.getValue().get(0));
            }
        }

        return values;
    }

    private List<String> splitByDelimiter(String value, Character delimiter) {
        List<String> result = new ArrayList<>();
        int startI = 0;

        for (int i = 0; i < value.length(); ++i) {
            if (value.charAt(i) == delimiter) {
                result.add(value.substring(startI, i));
                startI = i + 1;
            }
        }
        if (startI != 0) {
            result.add(value.substring(startI));
        }

        return result;
    }

    public abstract class AbstractMultiValuesConverter<T> implements FormattingTypeConverter<ConvertibleMultiValues, T, Format> {
        @Override
        public Optional<T> convert(
                ConvertibleMultiValues object, Class<T> targetType, ConversionContext conversionContext
        ) {
            if (!(conversionContext instanceof ArgumentConversionContext)) {
                return Optional.empty();
            }
            // noinspection unchecked
            ConvertibleMultiValues<String> parameters;
            ArgumentConversionContext<T> context = (ArgumentConversionContext<T>) conversionContext;
            try {
                parameters = object;
            } catch (Exception e) {
                return Optional.empty();
            }

            String format = conversionContext.getAnnotationMetadata().getValue(Format.class, String.class).orElse(null);
            if (format == null) {
                return Optional.empty();
            }

            String name = conversionContext.getAnnotationMetadata().getValue(Bindable.class, String.class)
                    .orElse(context.getArgument().getName());
            Optional<String> defaultValue = conversionContext.getAnnotationMetadata()
                    .getValue(Bindable.class, "defaultValue", String.class);

            switch (format) {
                case QUERY_VALUE_FORMAT_CSV:
                    return retrieveSeparatedValue(context, name, parameters, defaultValue, CSV_DELIMITER);
                case QUERY_VALUE_FORMAT_SSV:
                    return retrieveSeparatedValue(context, name, parameters, defaultValue, SSV_DELIMITER);
                case QUERY_VALUE_FORMAT_PIPES:
                    return retrieveSeparatedValue(context, name, parameters, defaultValue, PIPES_DELIMITER);
                case QUERY_VALUE_FORMAT_MULTI:
                    return retrieveMultiValue(context, name, parameters, defaultValue);
                case QUERY_VALUE_FORMAT_DEEP_OBJECT:
                    return retrieveDeepObjectValue(context, name, parameters, defaultValue);
                default:
                    return Optional.empty();
            }
        }

        protected abstract Optional<T> retrieveSeparatedValue(ArgumentConversionContext<T> conversionContext,
                String name, ConvertibleMultiValues<String> parameters, Optional<String> defaultValue, Character delimiter);

        protected abstract Optional<T> retrieveMultiValue(ArgumentConversionContext<T> conversionContext,
                String name, ConvertibleMultiValues<String> parameters, Optional<String> defaultValue);

        protected abstract Optional<T> retrieveDeepObjectValue(ArgumentConversionContext<T> conversionContext,
                String name, ConvertibleMultiValues<String> parameters, Optional<String> defaultValue);

        @Override
        public Class<Format> annotationType() {
            return Format.class;
        }
    }

    /**
     * A converter to convert from {@link ConvertibleMultiValues} to an {@link Iterable}.
     */
    public class IterableConverter extends AbstractMultiValuesConverter<Iterable> {
        @Override
        protected Optional<Iterable> retrieveSeparatedValue(ArgumentConversionContext<Iterable> conversionContext,
            String name, ConvertibleMultiValues<String> parameters, Optional<String> defaultValue, Character delimiter
        ) {
            List<String> values = parameters.getAll(name);
            if (values.size() == 0 && defaultValue.isPresent()) {
                values.add(defaultValue.get());
            }

            List<String> result = new ArrayList<>(values.size());
            for (String value: values) {
                result.addAll(splitByDelimiter(value, delimiter));
            }

            return convertValues(conversionContext, result);
        }

        @Override
        protected Optional<Iterable> retrieveMultiValue(ArgumentConversionContext<Iterable> conversionContext,
            String name, ConvertibleMultiValues<String> parameters, Optional<String> defaultValue
        ) {
            List<String> values = parameters.getAll(name);
            return convertValues(conversionContext, values);
        }

        @Override
        protected Optional<Iterable> retrieveDeepObjectValue(ArgumentConversionContext<Iterable> conversionContext,
            String name, ConvertibleMultiValues<String> parameters, Optional<String> defaultValue
        ) {
            List<String> values = new ArrayList<>();

            for (int i = 0;; ++i) {
                String key = name + '[' + i + ']';
                if (!parameters.contains(key)) {
                    break;
                }
                values.add(parameters.get(key));
            }

            return convertValues(conversionContext, values);
        }

        private Optional<Iterable> convertValues(ArgumentConversionContext<Iterable> context, List<String> values) {
            Argument<?> typeArgument = context.getArgument().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            List convertedValues;

            // Convert all the values
            if (typeArgument.getType().isAssignableFrom(String.class)) {
                convertedValues = values;
            } else {
                ArgumentConversionContext<?> argumentConversionContext = ConversionContext.of(typeArgument);
                convertedValues = new ArrayList<>(values.size());
                for (String value: values) {
                    conversionService.convert(value, argumentConversionContext).ifPresent(convertedValues::add);
                }
            }

            // Convert the collection itself
            return CollectionUtils.convertCollection((Class) context.getArgument().getType(), convertedValues);
        }
    }

    /**
     * A converter to convert from {@link ConvertibleMultiValues} to an {@link Map}.
     */
    public class MapConverter extends AbstractMultiValuesConverter<Map> {
        @Override
        protected Optional<Map> retrieveSeparatedValue(ArgumentConversionContext<Map> conversionContext,
             String name, ConvertibleMultiValues<String> parameters, Optional<String> defaultValue, Character delimiter
        ) {
            Map<String, String> values = getSeparatedMapParameters(parameters, name, defaultValue, delimiter);
            return convertValues(conversionContext, values);
        }

        @Override
        protected Optional<Map> retrieveMultiValue(ArgumentConversionContext<Map> conversionContext,
             String name, ConvertibleMultiValues<String> parameters, Optional<String> defaultValue
        ) {
            Map<String, String> values = getMultiMapParameters(parameters, name, defaultValue);
            return convertValues(conversionContext, values);
        }

        @Override
        protected Optional<Map> retrieveDeepObjectValue(ArgumentConversionContext<Map> conversionContext,
             String name, ConvertibleMultiValues<String> parameters, Optional<String> defaultValue
        ) {
            Map<String, String> values = getDeepObjectMapParameters(parameters, name, defaultValue);
            return convertValues(conversionContext, values);
        }

        private Optional<Map> convertValues(ArgumentConversionContext<Map> context, Map<String, String> values) {
            // There is no option to convert between maps
            if (!context.getArgument().getType().isAssignableFrom(values.getClass())) {
                return Optional.empty();
            }

            Argument<?>[] typeArguments = context.getTypeParameters();
            Argument<?> keyArgument = typeArguments.length > 0 ? typeArguments[0] : Argument.OBJECT_ARGUMENT;
            Argument<?> valueArgument = typeArguments.length > 1 ? typeArguments[1] : Argument.OBJECT_ARGUMENT;

            Map convertedValues;

            // Convert all the values
            if (keyArgument.getType().isAssignableFrom(String.class) &&
                    valueArgument.getType().isAssignableFrom(String.class)) {
                convertedValues = values;
            } else {
                ArgumentConversionContext<?> keyContext = ConversionContext.of(keyArgument);
                ArgumentConversionContext<?> valueContext = ConversionContext.of(valueArgument);

                convertedValues = new HashMap();
                for (Map.Entry<String, String> entry: values.entrySet()) {
                    Object value = conversionService.convert(entry.getValue(), valueContext).orElse(null);
                    if (value == null) {
                        continue;
                    }
                    Object key = conversionService.convert(entry.getKey(), keyContext).orElse(null);
                    if (key == null) {
                        continue;
                    }
                    convertedValues.put(key, value);
                }
            }

            // Convert the collection itself
            return Optional.of(convertedValues);
        }
    }

    /**
     * A converter to convert from {@link ConvertibleMultiValues} to a POJO {@link Object}.
     */
    public class ObjectConverter extends AbstractMultiValuesConverter<Object> {
        @Override
        protected Optional<Object> retrieveSeparatedValue(ArgumentConversionContext<Object> conversionContext,
             String name, ConvertibleMultiValues<String> parameters, Optional<String> defaultValue, Character delimiter
        ) {
            Map<String, String> values = getSeparatedMapParameters(parameters, name, defaultValue, delimiter);
            return convertValues(conversionContext, values);
        }

        @Override
        protected Optional<Object> retrieveMultiValue(ArgumentConversionContext<Object> conversionContext,
             String name, ConvertibleMultiValues<String> parameters, Optional<String> defaultValue
        ) {
            Map<String, String> values = getMultiMapParameters(parameters, name, defaultValue);
            return convertValues(conversionContext, values);
        }

        @Override
        protected Optional<Object> retrieveDeepObjectValue(ArgumentConversionContext<Object> conversionContext,
             String name, ConvertibleMultiValues<String> parameters, Optional<String> defaultValue
        ) {
            Map<String, String> values = getDeepObjectMapParameters(parameters, name, defaultValue);
            return convertValues(conversionContext, values);
        }

        private Optional<Object> convertValues(ArgumentConversionContext<Object> context, Map<String, String> values) {
            BeanIntrospection introspection = BeanIntrospection.getIntrospection(context.getArgument().getType());

            // Create with constructor
            BeanConstructor<?> constructor = introspection.getConstructor();
            Argument<?>[] constructorArguments = constructor.getArguments();
            Object[] constructorParameters = new Object[constructorArguments.length];
            for (int i = 0; i < constructorArguments.length; ++i) {
                Argument<?> argument = constructorArguments[i];
                String name = argument.getAnnotationMetadata().getValue(Bindable.class, String.class)
                        .orElse(argument.getName());
                constructorParameters[i] = conversionService.convert(values.get(name), ConversionContext.of(argument))
                        .orElse(null);
            }
            Object result = constructor.instantiate(constructorParameters);

            // Set the remaining properties with wrapper
            BeanWrapper<Object> wrapper = BeanWrapper.getWrapper(result);
            for (BeanProperty<Object, Object> property: wrapper.getBeanProperties()) {
                String name = property.getName();

                if (!property.isReadOnly() && values.containsKey(name)) {
                    conversionService.convert(values.get(name), ConversionContext.of(property.asArgument()))
                            .ifPresent(v -> property.set(result, v));
                }
            }

            return Optional.of(result);
        }
    }
}
