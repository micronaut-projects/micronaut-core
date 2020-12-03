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
package io.micronaut.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.*;

/**
 * Configuration for the Jackson JSON parser.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@ConfigurationProperties("jackson")
@TypeHint(
        value = {
                PropertyNamingStrategy.UpperCamelCaseStrategy.class,
                ArrayList.class,
                LinkedHashMap.class,
                HashSet.class
        })
public class JacksonConfiguration {

    /**
     * The default array size threshold value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_ARRAYSIZETHRESHOLD = 100;
    /**
     * The property used to enable module scan.
     */
    public static final String PROPERTY_MODULE_SCAN = "jackson.module-scan";
    /**
     * The property used to enable bean introspection.
     */
    public static final String PROPERTY_USE_BEAN_INTROSPECTION = "jackson.bean-introspection-module";

    private boolean moduleScan = true;
    private boolean beanIntrospectionModule = false;
    private String dateFormat;
    private Locale locale;
    private TimeZone timeZone;
    private int arraySizeThreshold = DEFAULT_ARRAYSIZETHRESHOLD;
    private Map<SerializationFeature, Boolean> serialization = Collections.emptyMap();
    private Map<DeserializationFeature, Boolean> deserialization = Collections.emptyMap();
    private Map<MapperFeature, Boolean> mapper = Collections.emptyMap();
    private Map<JsonParser.Feature, Boolean> parser = Collections.emptyMap();
    private Map<JsonGenerator.Feature, Boolean> generator = Collections.emptyMap();
    private JsonInclude.Include serializationInclusion = JsonInclude.Include.NON_EMPTY;
    private ObjectMapper.DefaultTyping defaultTyping = null;
    private PropertyNamingStrategy propertyNamingStrategy = null;
    private boolean alwaysSerializeErrorsAsList = false;

    /**
     * Whether the {@link io.micronaut.core.beans.BeanIntrospection} should be used for reflection free object serialialization/deserialialization.
     * @return True if it should
     */
    @Experimental
    public boolean isBeanIntrospectionModule() {
        return beanIntrospectionModule;
    }

    /**
     * Whether the {@link io.micronaut.core.beans.BeanIntrospection} should be used for reflection free object serialialization/deserialialization.
     *
     * @param beanIntrospectionModule True if it should
     */
    @Experimental
    public void setBeanIntrospectionModule(boolean beanIntrospectionModule) {
        this.beanIntrospectionModule = beanIntrospectionModule;
    }

    /**
     * Whether Jackson modules should be scanned for.
     *
     * @return True if module scanning is enabled
     */
    public boolean isModuleScan() {
        return moduleScan;
    }

    /**
     * Sets whether to scan for modules or not (defaults to true).
     * @param moduleScan True if module scan should be enabled
     */
    public void setModuleScan(boolean moduleScan) {
        this.moduleScan = moduleScan;
    }

    /**
     * @return The default serialization inclusion settings
     */
    public JsonInclude.Include getSerializationInclusion() {
        return serializationInclusion;
    }

    /**
     * @return The global defaultTyping using for Polymorphic handling
     */
    public ObjectMapper.DefaultTyping getDefaultTyping() {
        return defaultTyping;
    }

    /**
     * @return The default locale to use
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * @return The default time zone to use
     */
    public TimeZone getTimeZone() {
        return timeZone;
    }

    /**
     * @return The date format to use for dates
     */
    public String getDateFormat() {
        return dateFormat;
    }

    /**
     * @return The serialization settings
     */
    public Map<SerializationFeature, Boolean> getSerializationSettings() {
        return serialization;
    }

    /**
     * @return The deserialization settings
     */
    public Map<DeserializationFeature, Boolean> getDeserializationSettings() {
        return deserialization;
    }

    /**
     * @return Settings for the object mapper
     */
    public Map<MapperFeature, Boolean> getMapperSettings() {
        return mapper;
    }

    /**
     * @return Settings for the parser
     */
    public Map<JsonParser.Feature, Boolean> getParserSettings() {
        return parser;
    }

    /**
     * @return Settings for the generator
     */
    public Map<JsonGenerator.Feature, Boolean> getGeneratorSettings() {
        return generator;
    }

    /**
     * @return The array size threshold to use when using Jackson for data binding
     */
    public int getArraySizeThreshold() {
        return arraySizeThreshold;
    }

    /**
     * @return The property naming strategy
     */
    public PropertyNamingStrategy getPropertyNamingStrategy() {
        return propertyNamingStrategy;
    }

    /**
     * Whether _embedded.errors should always be serialized as list. If set to false, _embedded.errors
     * with 1 element will be serialized as an object.
     *
     * @return True if _embedded.errors should always be serialized as list.
     */
    public boolean isAlwaysSerializeErrorsAsList() {
        return alwaysSerializeErrorsAsList;
    }

    /**
     * Sets the default date format to use.
     * @param dateFormat The date format
     */
    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    /**
     * Sets the locale to use.
     * @param locale The locale
     */
    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    /**
     * Sets the timezone to use.
     * @param timeZone The timezone
     */
    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    /**
     * Sets the array size threshold for data binding. Default value ({@value #DEFAULT_ARRAYSIZETHRESHOLD}).
     * @param arraySizeThreshold The array size threshold
     */
    public void setArraySizeThreshold(int arraySizeThreshold) {
        this.arraySizeThreshold = arraySizeThreshold;
    }

    /**
     * Sets the serialization features to use.
     * @param serialization The serialization features.
     */
    public void setSerialization(Map<SerializationFeature, Boolean> serialization) {
        if (CollectionUtils.isNotEmpty(serialization)) {
            this.serialization = serialization;
        }
    }

    /**
     * Sets the deserialization features to use.
     * @param deserialization The deserialiation features.
     */
    public void setDeserialization(Map<DeserializationFeature, Boolean> deserialization) {
        if (CollectionUtils.isNotEmpty(deserialization)) {
            this.deserialization = deserialization;
        }
    }

    /**
     * Sets the object mapper features to use.
     * @param mapper The object mapper features
     */
    public void setMapper(Map<MapperFeature, Boolean> mapper) {
        if (CollectionUtils.isNotEmpty(mapper)) {
            this.mapper = mapper;
        }
    }

    /**
     * Sets the parser features to use.
     * @param parser The parser features
     */
    public void setParser(Map<JsonParser.Feature, Boolean> parser) {
        if (CollectionUtils.isNotEmpty(parser)) {
            this.parser = parser;
        }
    }

    /**
     * Sets the generator features to use.
     * @param generator The generator features
     */
    public void setGenerator(Map<JsonGenerator.Feature, Boolean> generator) {
        if (CollectionUtils.isNotEmpty(generator)) {
            this.generator = generator;
        }
    }

    /**
     * Sets the serialization inclusion mode.
     *
     * @param serializationInclusion The serialization inclusion mode
     */
    public void setSerializationInclusion(JsonInclude.Include serializationInclusion) {
        if (serializationInclusion != null) {
            this.serializationInclusion = serializationInclusion;
        }
    }

    /**
     * Sets the global defaultTyping using for Polymorphic handling.
     *
     * @param defaultTyping The defaultTyping
     */
    public void setDefaultTyping(ObjectMapper.DefaultTyping defaultTyping) {
        this.defaultTyping = defaultTyping;
    }

    /**
     * Sets the property naming strategy.
     *
     * @param propertyNamingStrategy The property naming strategy
     */
    public void setPropertyNamingStrategy(PropertyNamingStrategy propertyNamingStrategy) {
        this.propertyNamingStrategy = propertyNamingStrategy;
    }

    /**
     * Sets whether _embedded.errors should always be serialized as list (defaults to false).
     * If set to false, _embedded.errors with 1 element will be serialized as an object.
     *
     * @param alwaysSerializeErrorsAsList True if _embedded.errors should always be serialized as list.
     */
    public void setAlwaysSerializeErrorsAsList(boolean alwaysSerializeErrorsAsList) {
        this.alwaysSerializeErrorsAsList = alwaysSerializeErrorsAsList;
    }

    /**
     * Constructors a JavaType for the given argument and type factory.
     * @param type The type
     * @param typeFactory The type factory
     * @param <T> The generic type
     * @return The JavaType
     */
    public static <T> JavaType constructType(@NonNull Argument<T> type, @NonNull TypeFactory typeFactory) {
        ArgumentUtils.requireNonNull("type", type);
        ArgumentUtils.requireNonNull("typeFactory", typeFactory);
        Map<String, Argument<?>> typeVariables = type.getTypeVariables();
        JavaType[] objects = toJavaTypeArray(typeFactory, typeVariables);
        final Class<T> rawType = type.getType();
        if (ArrayUtils.isNotEmpty(objects)) {
            final JavaType javaType = typeFactory.constructType(
                    rawType
            );
            if (javaType.isCollectionLikeType()) {
                return typeFactory.constructCollectionLikeType(
                        rawType,
                        objects[0]
                );
            } else if (javaType.isMapLikeType()) {
                return typeFactory.constructMapLikeType(
                        rawType,
                        objects[0],
                        objects[1]
                );
            } else if (javaType.isReferenceType()) {
                return typeFactory.constructReferenceType(rawType, objects[0]);
            }
            return typeFactory.constructParametricType(rawType, objects);
        } else {
            return typeFactory.constructType(
                    rawType
            );
        }
    }

    private static JavaType[] toJavaTypeArray(TypeFactory typeFactory, Map<String, Argument<?>> typeVariables) {
        List<JavaType> javaTypes = new ArrayList<>();
        for (Argument<?> argument : typeVariables.values()) {
            if (argument.hasTypeVariables()) {
                javaTypes.add(typeFactory.constructParametricType(argument.getType(), toJavaTypeArray(typeFactory, argument.getTypeVariables())));
            } else {
                javaTypes.add(typeFactory.constructType(argument.getType()));
            }
        }
        return javaTypes.toArray(new JavaType[0]);
    }
}
