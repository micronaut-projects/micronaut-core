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
package io.micronaut.core.convert.converters;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.convert.format.FormattingTypeConverter;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.convert.value.MutableConvertibleMultiValuesMap;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A factory for creation of various {@link FormattingTypeConverter}s to and from {@link ConvertibleMultiValues} type.
 * The other types are either {@link Iterable} or {@link Map} or POJO {@link Object}.
 *
 * The converters only work when an {@link ArgumentConversionContext} is provided (so the type is an argument), as
 * the name of the parameter needs to be retrieved from there.
 *
 * Also {@link Format} annotation is required and needs to have one of the below-mentioned formats: "csv", "ssv",
 * "pipes", "multi", "deep-object". The format can be written in any case, e.g. "DEEP_OBJECT", "deep-object".
 *
 * @since 3.0.0
 * @author Andriy Dmytruk
 */
public class MultiValuesConverterFactory {
    /**
     * Values separated with commas ",". In case of iterables, the values are converted to {@link String} and joined
     * with comma delimiter. In case of {@link Map} or a POJO {@link Object} the keys and values are alternating and all
     * delimited with commas.
     * <table border="1">
     *     <caption>Examples</caption>
     *     <tr> <th><b> Type </b></th>      <th><b> Example value </b></th>                     <th><b> Example representation </b></th> </tr>
     *     <tr> <td> Iterable &emsp; </td>   <td> param=["Mike", "Adam", "Kate"] </td>           <td> "param=Mike,Adam,Kate" </td></tr>
     *     <tr> <td> Map </td>              <td> param=["name": "Mike", "age": "30"] &emsp;</td> <td> "param=name,Mike,age,30" </td> </tr>
     *     <tr> <td> Object </td>           <td> param={name: "Mike", age: 30} </td>            <td> "param=name,Mike,age,30" </td> </tr>
     * </table>
     * Note that ambiguity may arise when the values contain commas themselves after being converted to String.
     */
    public static final String FORMAT_CSV = "csv";

    /**
     * Values separated with spaces " " similarly to CSV being separated with commas.
     */
    public static final String FORMAT_SSV = "ssv";

    /**
     * Values separated with the pipe "|" symbol similarly to CSV being separated with commas.
     */
    public static final String FORMAT_PIPES = "pipes";

    /**
     * Values are repeated with the same parameter name for {@link Iterable}, while {@link Map} and POJO {@link Object}
     * would be expanded with its property names.
     * <table border="1">
     *     <caption>Examples</caption>
     *     <tr> <th><b> Type </b></th>      <th><b> Example value </b></th>                        <th><b> Example representation </b></th> </tr>
     *     <tr> <td> Iterable &emsp; </td>   <td> param=["Mike", "Adam", "Kate"] </td>              <td> "param=Mike&amp;param=Adam&amp;param=Kate </td></tr>
     *     <tr> <td> Map </td>              <td> param=["name": "Mike", "age": "30"] &emsp; </td>   <td> "name=Mike&amp;age=30" </td> </tr>
     *     <tr> <td> Object </td>           <td> param={name: "Mike", age: 30} </td>               <td> "name=Mike&amp;age=30" </td> </tr>
     * </table>
     */
    public static final String FORMAT_MULTI = "multi";

    /**
     * Values are put in the representation with property name for {@link Map} and POJO {@link Object} in square
     * after the original parameter name.
     * <table border="1">
     *     <caption>Examples</caption>
     *     <tr> <th><b> Type </b></th>      <th><b> Example value </b></th>                        <th><b> Example representation </b></th> </tr>
     *     <tr> <td> Iterable &emsp; </td>   <td> param=["Mike", "Adam", "Kate"] </td>              <td> "param[0]=Mike&amp;param[1]=Adam&amp;param[2]=Kate </td></tr>
     *     <tr> <td> Map </td>              <td> param=["name": "Mike", "age": "30"] &emsp; </td>   <td> "param[name]=Mike&amp;param[age]=30" </td> </tr>
     *     <tr> <td> Object </td>           <td> param={name: "Mike", age: 30} </td>               <td> "param[name]=Mike&amp;param[age]=30" </td> </tr>
     * </table>
     */
    public static final String FORMAT_DEEP_OBJECT = "deepobject";

    private static final Character CSV_DELIMITER = ',';
    private static final Character SSV_DELIMITER = ' ';
    private static final Character PIPES_DELIMITER = '|';

    /**
     * Convert given string value to normalized format, so that it can be compared independent of case.
     */
    private static String normalizeFormatName(String value) {
        return value.toLowerCase().replaceAll("[-_]", "");
    }

    /**
     * A common function for {@link MultiValuesToMapConverter} and {@link MultiValuesToObjectConverter}.
     * Retrieves parameter that is separated by delimiter given its name from all the parameters
     *
     * @return All the values in a Map
     */
    private static Map<String, String> getSeparatedMapParameters(
            ConvertibleMultiValues<String> parameters, String name, String defaultValue, Character delimiter
    ) {
        List<String> paramValues = parameters.getAll(name);

        if (paramValues.isEmpty() && defaultValue != null) {
            paramValues.add(defaultValue);
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
     * A common function for {@link MultiValuesToMapConverter} and {@link MultiValuesToObjectConverter}.
     * Retrieves the values of parameter from all the parameters that is in MULTI format given its name
     *
     * @return All the values in a Map
     */
    private static Map<String, String> getMultiMapParameters(ConvertibleMultiValues<String> parameters) {
        // Convert to map of strings - if multiple values are present, the first one is taken
        return parameters.asMap().entrySet().stream()
                .filter(v -> !v.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, v -> v.getValue().get(0)));
    }

    /**
     * A common function for {@link MultiValuesToMapConverter} and {@link MultiValuesToObjectConverter}.
     * Retrieves the values of parameter from all the parameters that is in DEEP_OBJECT FORMAT given its name
     *
     * @return All the values in a Map
     */
    private static Map<String, String> getDeepObjectMapParameters(ConvertibleMultiValues<String> parameters, String name) {
        Map<String, List<String>> paramValues = parameters.asMap();
        Map<String, String> values = new HashMap<>();

        // Convert to map of strings - if multiple values are present, only first one is taken
        for (Map.Entry<String, List<String>> param: paramValues.entrySet()) {
            String key = param.getKey();
            if (key.startsWith(name) && key.length() > name.length() &&
                    key.charAt(name.length()) == '[' && key.charAt(key.length() - 1) == ']' &&
                    !param.getValue().isEmpty()
            ) {
                String mapKey = key.substring(name.length() + 1, key.length() - 1);
                values.put(mapKey, param.getValue().get(0));
            }
        }

        return values;
    }

    /**
     * Splits string given a delimiter.
     */
    private static List<String> splitByDelimiter(String value, Character delimiter) {
        List<String> result = new ArrayList<>();
        int startI = 0;

        for (int i = 0; i < value.length(); ++i) {
            if (value.charAt(i) == delimiter) {
                result.add(value.substring(startI, i));
                startI = i + 1;
            }
        }
        if (!value.isEmpty()) {
            result.add(value.substring(startI));
        }

        return result;
    }

    /**
     * Join strings given a delimiter.
     * @param strings strings to join
     * @param delimiter the delimiter
     * @return joined string
     */
    private static String joinStrings(Iterable<String> strings, Character delimiter) {
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
     * An abstract class for converters from {@link ConvertibleMultiValues}.
     * @param <T>
     */
    private abstract static class AbstractConverterFromMultiValues<T>
            implements FormattingTypeConverter<ConvertibleMultiValues, T, Format> {
        protected ConversionService conversionService;

        AbstractConverterFromMultiValues(ConversionService conversionService) {
            this.conversionService = conversionService;
        }

        /**
         * Implemented convert function that checks which Format is specified inside the {@link Format} annotation
         * If one is specified, it calls one of the corresponding abstract methods. Otherwise, empty optional is returned
         */
        @Override
        public Optional<T> convert(
                ConvertibleMultiValues object, Class<T> targetType, ConversionContext conversionContext
        ) {
            if (!(conversionContext instanceof ArgumentConversionContext)) {
                return Optional.empty();
            }
            ConvertibleMultiValues<String> parameters = (ConvertibleMultiValues<String>) object;
            ArgumentConversionContext<T> context = (ArgumentConversionContext<T>) conversionContext;

            String format = conversionContext.getAnnotationMetadata()
                    .stringValue(Format.class).orElse(null);
            if (format == null) {
                return Optional.empty();
            }

            String name = conversionContext.getAnnotationMetadata().stringValue(Bindable.class)
                    .orElse(context.getArgument().getName());
            String defaultValue = conversionContext.getAnnotationMetadata()
                    .stringValue(Bindable.class, "defaultValue")
                    .orElse(null);

            switch (normalizeFormatName(format)) {
                case FORMAT_CSV:
                    return retrieveSeparatedValue(context, name, parameters, defaultValue, CSV_DELIMITER);
                case FORMAT_SSV:
                    return retrieveSeparatedValue(context, name, parameters, defaultValue, SSV_DELIMITER);
                case FORMAT_PIPES:
                    return retrieveSeparatedValue(context, name, parameters, defaultValue, PIPES_DELIMITER);
                case FORMAT_MULTI:
                    return retrieveMultiValue(context, name, parameters);
                case FORMAT_DEEP_OBJECT:
                    return retrieveDeepObjectValue(context, name, parameters);
                default:
                    return Optional.empty();
            }
        }

        /**
         * Method to retrieve the values from a separated parameter and return the parameter in desired type.
         *
         * @param conversionContext the conversion context of the value to which conversion is done
         *                          (including type and annotations)
         * @param name the name of the parameter
         * @param parameters all the parameters from which the parameter of given name needs to be retrieved
         * @param defaultValue default value
         * @param delimiter the delimiter of the values in the parameter String
         * @return the converted value if conversion was successful
         */
        protected abstract Optional<T> retrieveSeparatedValue(ArgumentConversionContext<T> conversionContext,
                                                              String name,
                                                              ConvertibleMultiValues<String> parameters,
                                                              @Nullable String defaultValue,
                                                              Character delimiter);

        /**
         * Method to retrieve the values from a parameter in MULTI format and return in desired type.
         *
         * @param conversionContext the conversion context of the value to which conversion is done
         *                          (including type and annotations)
         * @param name the name of the parameter
         * @param parameters all the parameters from which the parameter of given name needs to be retrieved
         * @return the converted value if conversion was successful
         */
        protected abstract Optional<T> retrieveMultiValue(ArgumentConversionContext<T> conversionContext,
                                                          String name,
                                                          ConvertibleMultiValues<String> parameters);

        /**
         * Method to retrieve the values from a parameter in DEEP_OBJECT format and return in desired type.
         *
         * @param conversionContext the conversion context of the value to which conversion is done
         *                          (including type and annotations)
         * @param name the name of the parameter
         * @param parameters all the parameters from which the parameter of given name needs to be retrieved
         * @return the converted value if conversion was successful*/
        protected abstract Optional<T> retrieveDeepObjectValue(ArgumentConversionContext<T> conversionContext,
                                                               String name,
                                                               ConvertibleMultiValues<String> parameters);

        @Override
        public Class<Format> annotationType() {
            return Format.class;
        }
    }

    /**
     * A converter to convert from {@link ConvertibleMultiValues} to an {@link Iterable}.
     */
    public static class MultiValuesToIterableConverter extends AbstractConverterFromMultiValues<Iterable> {
        public MultiValuesToIterableConverter(ConversionService conversionService) {
            super(conversionService);
        }

        @Override
        protected Optional<Iterable> retrieveSeparatedValue(ArgumentConversionContext<Iterable> conversionContext,
            String name, ConvertibleMultiValues<String> parameters, String defaultValue, Character delimiter
        ) {
            List<String> values = parameters.getAll(name);
            if (values.isEmpty() && defaultValue != null) {
                values.add(defaultValue);
            }

            List<String> result = new ArrayList<>(values.size());
            for (String value: values) {
                result.addAll(splitByDelimiter(value, delimiter));
            }

            return convertValues(conversionContext, result);
        }

        @Override
        protected Optional<Iterable> retrieveMultiValue(ArgumentConversionContext<Iterable> conversionContext,
                                                        String name,
                                                        ConvertibleMultiValues<String> parameters) {
            List<String> values = parameters.getAll(name);
            return convertValues(conversionContext, values);
        }

        @Override
        protected Optional<Iterable> retrieveDeepObjectValue(ArgumentConversionContext<Iterable> conversionContext,
                                                             String name,
                                                             ConvertibleMultiValues<String> parameters) {
            List<String> values = new ArrayList<>();

            for (int i = 0;; ++i) {
                String key = name + '[' + i + ']';
                String value = parameters.get(key);
                if (value == null) {
                    break;
                }
                values.add(value);
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
    public static class MultiValuesToMapConverter extends AbstractConverterFromMultiValues<Map> {
        public MultiValuesToMapConverter(ConversionService conversionService) {
            super(conversionService);
        }

        @Override
        protected Optional<Map> retrieveSeparatedValue(ArgumentConversionContext<Map> conversionContext,
                                                       String name,
                                                       ConvertibleMultiValues<String> parameters,
                                                       String defaultValue,
                                                       Character delimiter
        ) {
            Map<String, String> values = getSeparatedMapParameters(parameters, name, defaultValue, delimiter);
            return convertValues(conversionContext, values);
        }

        @Override
        protected Optional<Map> retrieveMultiValue(ArgumentConversionContext<Map> conversionContext,
                                                   String name,
                                                   ConvertibleMultiValues<String> parameters
        ) {
            Map<String, String> values = getMultiMapParameters(parameters);
            return convertValues(conversionContext, values);
        }

        @Override
        protected Optional<Map> retrieveDeepObjectValue(ArgumentConversionContext<Map> conversionContext,
                                                        String name,
                                                        ConvertibleMultiValues<String> parameters
        ) {
            Map<String, String> values = getDeepObjectMapParameters(parameters, name);
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
    public static class MultiValuesToObjectConverter extends AbstractConverterFromMultiValues<Object> {

        public MultiValuesToObjectConverter(ConversionService conversionService) {
            super(conversionService);
        }

        @Override
        protected Optional<Object> retrieveSeparatedValue(ArgumentConversionContext<Object> conversionContext,
            String name, ConvertibleMultiValues<String> parameters, String defaultValue, Character delimiter
        ) {
            Map<String, String> values = getSeparatedMapParameters(parameters, name, defaultValue, delimiter);
            return convertValues(conversionContext, values);
        }

        @Override
        protected Optional<Object> retrieveMultiValue(ArgumentConversionContext<Object> conversionContext,
                                                      String name,
                                                      ConvertibleMultiValues<String> parameters
        ) {
            Map<String, String> values = getMultiMapParameters(parameters);
            return convertValues(conversionContext, values);
        }

        @Override
        protected Optional<Object> retrieveDeepObjectValue(ArgumentConversionContext<Object> conversionContext,
                                                           String name,
                                                           ConvertibleMultiValues<String> parameters
        ) {
            Map<String, String> values = getDeepObjectMapParameters(parameters, name);
            return convertValues(conversionContext, values);
        }

        private Optional<Object> convertValues(ArgumentConversionContext<Object> context, Map<String, String> values) {
            try {
                BeanIntrospection introspection = BeanIntrospection.getIntrospection(context.getArgument().getType());

                // Create with constructor
                BeanConstructor<?> constructor = introspection.getConstructor();
                Argument<?>[] constructorArguments = constructor.getArguments();
                Object[] constructorParameters = new Object[constructorArguments.length];
                for (int i = 0; i < constructorArguments.length; ++i) {
                    Argument<?> argument = constructorArguments[i];
                    String name = argument.getAnnotationMetadata().stringValue(Bindable.class)
                            .orElse(argument.getName());
                    constructorParameters[i] = conversionService.convert(values.get(name), ConversionContext.of(argument))
                            .orElse(null);
                }
                Object result = constructor.instantiate(constructorParameters);

                // Set the remaining properties with wrapper
                BeanWrapper<Object> wrapper = BeanWrapper.getWrapper(result);
                for (BeanProperty<Object, Object> property : wrapper.getBeanProperties()) {
                    String name = property.getName();

                    if (!property.isReadOnly() && values.containsKey(name)) {
                        conversionService.convert(values.get(name), ConversionContext.of(property.asArgument()))
                                .ifPresent(v -> property.set(result, v));
                    }
                }

                return Optional.of(result);
            } catch (IntrospectionException e) {
                context.reject(values, e);
                return Optional.empty();
            }
        }
    }

    /**
     * An abstract class to convert to ConvertibleMultiValues.
     * @param <T> The class from which to convert
     */
    public abstract static class AbstractConverterToMultiValues<T>
            implements FormattingTypeConverter<T, ConvertibleMultiValues, Format> {
        protected ConversionService conversionService;

        public AbstractConverterToMultiValues(ConversionService conversionService) {
            this.conversionService = conversionService;
        }

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

            String format = conversionContext.getAnnotationMetadata().stringValue(Format.class).orElse(null);
            if (format == null) {
                return Optional.empty();
            }

            String name = conversionContext.getAnnotationMetadata().stringValue(Bindable.class)
                    .orElse(context.getArgument().getName());

            MutableConvertibleMultiValuesMap<String> parameters = new MutableConvertibleMultiValuesMap<>();
            if (object == null) {
                return Optional.of(parameters);
            }

            switch (normalizeFormatName(format)) {
                case FORMAT_CSV:
                    addSeparatedValues(context, name, object, parameters, CSV_DELIMITER);
                    break;
                case FORMAT_SSV:
                    addSeparatedValues(context, name, object, parameters, SSV_DELIMITER);
                    break;
                case FORMAT_PIPES:
                    addSeparatedValues(context, name, object, parameters, PIPES_DELIMITER);
                    break;
                case FORMAT_MULTI:
                    addMutliValues(context, name, object, parameters);
                    break;
                case FORMAT_DEEP_OBJECT:
                    addDeepObjectValues(context, name, object, parameters);
                    break;
                default:
                    return Optional.empty();
            }

            return Optional.of(parameters);
        }

        /**
         * Method that adds given value to the parameters in a format separated by a delimiter.
         *
         * @param context - the context of conversion which has the source type and any present annotations
         * @param name - the name of the parameter
         * @param object - the object that we are converting from
         * @param parameters - the parameters to the value of additional parameter will be added
         * @param delimiter - the required delimiter of the values in the parameter String
         */
        protected abstract void addSeparatedValues(ArgumentConversionContext<Object> context, String name,
                T object, MutableConvertibleMultiValuesMap<String> parameters, Character delimiter);

        /**
         * Method that adds given value to the parameters in a MULTI format.
         *
         * @param context - the context of conversion which has the source type and any present annotations
         * @param name - the name of the parameter
         * @param object - the object that we are converting from
         * @param parameters - the parameters to the value of additional parameter will be added
         */
        protected abstract void addMutliValues(ArgumentConversionContext<Object> context, String name,
                T object, MutableConvertibleMultiValuesMap<String> parameters);

        /**
         * Method that adds given value to the parameters in A DEEP_OBJECT format.
         *
         * @param context - the context of conversion which has the source type and any present annotations
         * @param name - the name of the parameter
         * @param object - the object that we are converting from
         * @param parameters - the parameters to the value of additional parameter will be added
         */
        protected abstract void addDeepObjectValues(ArgumentConversionContext<Object> context, String name,
                T object, MutableConvertibleMultiValuesMap<String> parameters);

        @Override
        public Class<Format> annotationType() {
            return Format.class;
        }
    }

    /**
     * A converter from {@link Iterable} to {@link ConvertibleMultiValues}.
     */
    public static class IterableToMultiValuesConverter extends AbstractConverterToMultiValues<Iterable> {
        public IterableToMultiValuesConverter(ConversionService conversionService) {
            super(conversionService);
        }

        private void processValues(ArgumentConversionContext<Object> context, Iterable object, Consumer<String> consumer) {
            ArgumentConversionContext<String> conversionContext = ConversionContext.STRING.with(
                    context.getFirstTypeVariable().map(Argument::getAnnotationMetadata).orElse(AnnotationMetadata.EMPTY_METADATA));
            for (Object value: object) {
                conversionService.convert(value, conversionContext).ifPresent(v -> consumer.accept(v));
            }
        }

        @Override
        protected void addSeparatedValues(ArgumentConversionContext<Object> context, String name,
            Iterable object, MutableConvertibleMultiValuesMap<String> parameters, Character delimiter
        ) {
            List<String> strings = new ArrayList<>();
            processValues(context, object, v -> strings.add(v));
            parameters.add(name, joinStrings(strings, delimiter));
        }

        @Override
        protected void addMutliValues(ArgumentConversionContext<Object> context, String name,
            Iterable object, MutableConvertibleMultiValuesMap<String> parameters
        ) {
            processValues(context, object, v -> parameters.add(name, v));
        }

        @Override
        protected void addDeepObjectValues(ArgumentConversionContext<Object> context, String name,
            Iterable object, MutableConvertibleMultiValuesMap<String> parameters
        ) {
            ArgumentConversionContext<String> conversionContext = ConversionContext.STRING.with(
                    context.getFirstTypeVariable().map(Argument::getAnnotationMetadata)
                            .orElse(AnnotationMetadata.EMPTY_METADATA));

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
    public static class MapToMultiValuesConverter extends AbstractConverterToMultiValues<Map> {
        public MapToMultiValuesConverter(ConversionService conversionService) {
            super(conversionService);
        }

        private void processValues(
                ArgumentConversionContext<Object> context, Map object, BiConsumer<String, String> consumer
        ) {
            // Build conversion context based on annotation metadata for both key and value
            Argument<?>[] typeParameters = context.getTypeParameters();
            ArgumentConversionContext<String> keyConversionContext = ConversionContext.STRING.with(
                    typeParameters.length > 0 ? typeParameters[0].getAnnotationMetadata() : AnnotationMetadata.EMPTY_METADATA);
            ArgumentConversionContext<String> valueConversionContext = ConversionContext.STRING.with(
                    typeParameters.length > 1 ? typeParameters[1].getAnnotationMetadata() : AnnotationMetadata.EMPTY_METADATA);

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
            Map object, MutableConvertibleMultiValuesMap<String> parameters, Character delimiter
        ) {
            List<String> values = new ArrayList<>();
            processValues(context, object, (k, v) -> {
                values.add(k);
                values.add(v);
            });
            parameters.add(name, joinStrings(values, delimiter));
        }

        @Override
        protected void addMutliValues(ArgumentConversionContext<Object> context, String name,
            Map object, MutableConvertibleMultiValuesMap<String> parameters
        ) {
            processValues(context, object, parameters::add);
        }

        @Override
        protected void addDeepObjectValues(ArgumentConversionContext<Object> context, String name,
           Map object, MutableConvertibleMultiValuesMap<String> parameters
        ) {
           processValues(context, object, (k, v) -> parameters.add(name + "[" + k + "]", v));
        }
    }

    /**
     * A converter from generic {@link Object} to {@link ConvertibleMultiValues}.
     */
    public static class ObjectToMultiValuesConverter extends AbstractConverterToMultiValues<Object> {
        public ObjectToMultiValuesConverter(ConversionService conversionService) {
            super(conversionService);
        }

        private void processValues(ArgumentConversionContext<Object> context,
                                   Object object,
                                   BiConsumer<String, String> consumer) {
            BeanWrapper<Object> beanWrapper;
            try {
                beanWrapper = BeanWrapper.getWrapper(object);
            } catch (IntrospectionException e) {
                context.reject(object, e);
                return;
            }

            for (BeanProperty<Object, Object> property: beanWrapper.getBeanProperties()) {
                String key = property.stringValue(Bindable.class).orElse(property.getName());
                ArgumentConversionContext<String> conversionContext =
                        ConversionContext.STRING.with(property.getAnnotationMetadata());
                conversionService.convert(property.get(object), conversionContext).ifPresent(value -> {
                    consumer.accept(key, value);
                });
            }
        }

        @Override
        protected void addSeparatedValues(ArgumentConversionContext<Object> context,
                                          String name,
                                          Object object,
                                          MutableConvertibleMultiValuesMap<String> parameters,
                                          Character delimiter) {
            List<String> values = new ArrayList<>();
            processValues(context, object, (k, v) -> {
                values.add(k);
                values.add(v);
            });
            parameters.add(name, joinStrings(values, delimiter));
        }

        @Override
        protected void addMutliValues(ArgumentConversionContext<Object> context,
                                      String name,
                                      Object object,
                                      MutableConvertibleMultiValuesMap<String> parameters) {
            processValues(context, object, parameters::add);
        }

        @Override
        protected void addDeepObjectValues(ArgumentConversionContext<Object> context,
                                           String name,
                                           Object object,
                                           MutableConvertibleMultiValuesMap<String> parameters) {
            processValues(context, object, (k, v) -> parameters.add(name + "[" + k + "]", v));
        }
    }
}
