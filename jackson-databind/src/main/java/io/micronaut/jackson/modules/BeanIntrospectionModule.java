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
package io.micronaut.jackson.modules;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyMetadata;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.CreatorProperty;
import com.fasterxml.jackson.databind.deser.NullValueProvider;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.impl.MethodProperty;
import com.fasterxml.jackson.databind.deser.std.StdValueInstantiator;
import com.fasterxml.jackson.databind.introspect.AccessorNamingStrategy;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import com.fasterxml.jackson.databind.introspect.AnnotationCollector;
import com.fasterxml.jackson.databind.introspect.DefaultAccessorNamingStrategy;
import com.fasterxml.jackson.databind.introspect.TypeResolutionContext;
import com.fasterxml.jackson.databind.introspect.VirtualAnnotatedMember;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.AnyGetterWriter;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerBuilder;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.impl.PropertySerializerMap;
import com.fasterxml.jackson.databind.ser.std.MapSerializer;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.SimpleBeanPropertyDefinition;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.UnsafeBeanInstantiationIntrospection;
import io.micronaut.core.beans.UnsafeBeanProperty;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.jackson.JacksonConfiguration;
import io.micronaut.jackson.JacksonDeserializationPreInstantiateCallback;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A Jackson module that adds reflection-free bean serialization and deserialization for Micronaut.
 *
 * @author graemerocher
 * @since 1.1
 */
@Internal
@Experimental
@Singleton
@Requires(property = JacksonConfiguration.PROPERTY_USE_BEAN_INTROSPECTION, notEquals = StringUtils.FALSE)
public class BeanIntrospectionModule extends SimpleModule {

    private static final Logger LOG = LoggerFactory.getLogger(BeanIntrospectionModule.class);

    /**
     * For testing.
     */
    boolean ignoreReflectiveProperties = false;

    /**
     * The pre-instantiate callback.
     */
    @Nullable
    private final JacksonDeserializationPreInstantiateCallback preInstantiateCallback;

    /**
     * Default constructor.
     */
    public BeanIntrospectionModule() {
        this(null);
    }

    /**
     * The constructor.
     *
     * @param preInstantiateCallback The optional instance {@link JacksonDeserializationPreInstantiateCallback}
     */
    @Inject
    public BeanIntrospectionModule(@Nullable JacksonDeserializationPreInstantiateCallback preInstantiateCallback) {
        this.preInstantiateCallback = preInstantiateCallback;
        setDeserializerModifier(new BeanIntrospectionDeserializerModifier());
        setSerializerModifier(new BeanIntrospectionSerializerModifier());
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);

        ObjectCodec owner = context.getOwner();
        if (owner instanceof ObjectMapper mapper) {
            mapper.setConfig(mapper.getSerializationConfig().with(new BeanIntrospectionAccessorNamingStrategyProvider(mapper.getSerializationConfig().getAccessorNaming())));
            mapper.setConfig(mapper.getDeserializationConfig().with(new BeanIntrospectionAccessorNamingStrategyProvider(mapper.getDeserializationConfig().getAccessorNaming())));
        }
    }

    /**
     * Find an introspection for the given class.
     *
     * @param beanClass The bean class
     * @return The introspection
     */
    @Nullable
    protected BeanIntrospection<Object> findIntrospection(Class<?> beanClass) {
        return (BeanIntrospection<Object>) BeanIntrospector.SHARED.findIntrospection(beanClass).orElse(null);
    }

    private JavaType newType(Argument<?> argument, TypeFactory typeFactory) {
        return JacksonConfiguration.constructType(argument, typeFactory);
    }

    private PropertyMetadata newPropertyMetadata(Argument<?> argument, AnnotationMetadata annotationMetadata) {
        final Boolean required = annotationMetadata.booleanValue(JsonProperty.class, "required").orElse(false);

        int index = annotationMetadata.intValue(JsonProperty.class, "index").orElse(-1);
        return PropertyMetadata.construct(
            required,
            annotationMetadata.stringValue(JsonPropertyDescription.class).orElse(null),
            index > -1 ? index : null,
            annotationMetadata.stringValue(JsonProperty.class, "defaultValue").orElse(null)
        );
    }

    // copied from VirtualBeanPropertyWriter
    private static boolean suppressNulls(JsonInclude.Value inclusion) {
        if (inclusion == null) {
            return false;
        }
        JsonInclude.Include incl = inclusion.getValueInclusion();
        return (incl != JsonInclude.Include.ALWAYS) && (incl != JsonInclude.Include.USE_DEFAULTS);
    }

    // copied from VirtualBeanPropertyWriter
    private static Object suppressableValue(JsonInclude.Value inclusion) {
        if (inclusion == null) {
            return false; // [sic]
        }
        JsonInclude.Include incl = inclusion.getValueInclusion();
        if ((incl == JsonInclude.Include.ALWAYS)
            || (incl == JsonInclude.Include.NON_NULL)
            || (incl == JsonInclude.Include.USE_DEFAULTS)) {
            return null;
        }
        return BeanPropertyWriter.MARKER_FOR_EMPTY;
    }

    /**
     * Parse a {@link JsonSerialize} or {@link JsonDeserialize} annotation.
     */
    private <T> T findSerializerFromAnnotation(BeanProperty<?, ?> beanProperty, Class<? extends Annotation> annotationType) {
        AnnotationValue<?> jsonSerializeAnnotation = beanProperty.getAnnotation(annotationType);
        if (jsonSerializeAnnotation != null) {
            // ideally, we'd use SerializerProvider here, but it's not exposed to the BeanSerializerModifier
            Class<?> using = jsonSerializeAnnotation.classValue("using").orElse(null);
            if (using != null) {
                BeanIntrospection<Object> usingIntrospection = findIntrospection(using);
                if (usingIntrospection != null) {
                    return (T) usingIntrospection.instantiate();
                } else {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Cannot construct {}, please add the @Introspected annotation to the class declaration", using.getName());
                    }
                }
            }
        }
        return null;
    }

    @NonNull
    private JsonFormat.Value parseJsonFormat(@NonNull AnnotationValue<JsonFormat> formatAnnotation) {
        return new JsonFormat.Value(
            formatAnnotation.stringValue("pattern").orElse(""),
            formatAnnotation.enumValue("shape", JsonFormat.Shape.class).orElse(JsonFormat.Shape.ANY),
            formatAnnotation.stringValue("locale").orElse(JsonFormat.DEFAULT_LOCALE),
            formatAnnotation.stringValue("timezone").orElse(JsonFormat.DEFAULT_TIMEZONE),
            JsonFormat.Features.construct(
                formatAnnotation.enumValues("with", JsonFormat.Feature.class),
                formatAnnotation.enumValues("without", JsonFormat.Feature.class)
            ),
            formatAnnotation.enumValue("lenient", OptBoolean.class).orElse(OptBoolean.DEFAULT).asBoolean()
        );
    }

    @Nullable
    private PropertyNamingStrategy findNamingStrategy(MapperConfig<?> mapperConfig, BeanIntrospection<?> introspection) {
        AnnotationValue<JsonNaming> namingAnnotation = introspection.getAnnotation(JsonNaming.class);
        if (namingAnnotation != null) {
            Optional<Class<?>> clazz = namingAnnotation.classValue();
            if (clazz.isPresent()) {
                try {
                    Constructor<?> emptyConstructor = clazz.get().getConstructor();
                    return (PropertyNamingStrategy) emptyConstructor.newInstance();
                } catch (NoSuchMethodException ignored) {
                    return mapperConfig.getPropertyNamingStrategy();
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Failed to construct configured PropertyNamingStrategy", e);
                }
            }
        }
        return mapperConfig.getPropertyNamingStrategy();
    }

    private String getName(MapperConfig<?> mapperConfig, @Nullable PropertyNamingStrategy namingStrategy, AnnotatedElement property) {
        String explicitName = property.getAnnotationMetadata().stringValue(JsonProperty.class).orElse(JsonProperty.USE_DEFAULT_NAME);
        if (!explicitName.equals(JsonProperty.USE_DEFAULT_NAME)) {
            return explicitName;
        }
        String implicitName = property.getName();
        if (namingStrategy != null) {
            return namingStrategy.nameForGetterMethod(mapperConfig, null, implicitName);
        } else {
            return implicitName;
        }
    }

    private static class IntrospectionVirtualAnnotatedMember extends VirtualAnnotatedMember {
        private final AnnotationMetadata annotationMetadata;

        public IntrospectionVirtualAnnotatedMember(TypeResolutionContext typeResolutionContext, Class<?> beanClass, String name, JavaType javaType, AnnotationMetadata annotationMetadata) {
            super(typeResolutionContext, beanClass, name, javaType);
            this.annotationMetadata = annotationMetadata;
        }

        @Override
        public boolean hasOneOf(Class<? extends Annotation>[] annoClasses) {
            return Arrays.stream(annoClasses).anyMatch(annotationMetadata::hasAnnotation);
        }
    }

    /**
     * Modifies bean serialization.
     */
    private class BeanIntrospectionSerializerModifier extends BeanSerializerModifier {
        @Override
        public BeanSerializerBuilder updateBuilder(SerializationConfig config, BeanDescription beanDesc, BeanSerializerBuilder builder) {
            final Class<?> beanClass = beanDesc.getBeanClass();
            final BeanIntrospection<Object> introspection = findIntrospection(beanClass);

            if (introspection == null) {
                return super.updateBuilder(config, beanDesc, builder);
            } else {
                PropertyNamingStrategy namingStrategy = findNamingStrategy(config, introspection);

                final BeanSerializerBuilder newBuilder = new BeanSerializerBuilder(beanDesc) {
                    @Override
                    public JsonSerializer<?> build() {
                        setConfig(config);
                        try {
                            return super.build();
                        } catch (RuntimeException e) {
                            if (LOG.isErrorEnabled()) {
                                LOG.error("Error building bean serializer for type [{}]: {}", beanClass, e.getMessage(), e);
                            }
                            throw e;
                        }
                    }
                };

                newBuilder.setAnyGetter(builder.getAnyGetter());
                final List<BeanPropertyWriter> properties = builder.getProperties();
                final Collection<BeanProperty<Object, Object>> beanProperties = introspection.getBeanProperties();
                if (ignoreReflectiveProperties || (CollectionUtils.isEmpty(properties) && CollectionUtils.isNotEmpty(beanProperties))) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Bean {} has no properties, while BeanIntrospection does. Recreating from introspection.", beanClass);
                    }
                    TypeResolutionContext typeResolutionContext = new TypeResolutionContext.Empty(config.getTypeFactory());
                    final List<BeanPropertyWriter> newProperties = new ArrayList<>(beanProperties.size());
                    for (BeanProperty<Object, Object> bp : beanProperties) {
                        if (bp.hasAnnotation(JsonIgnore.class) || bp.hasAnnotation(JsonValue.class)) {
                            continue;
                        }
                        UnsafeBeanProperty<Object, Object> beanProperty = (UnsafeBeanProperty<Object, Object>) bp;
                        final String propertyName = getName(config, namingStrategy, beanProperty);
                        JsonSerializer<?> serializerFromAnnotation = findSerializerFromAnnotation(beanProperty, JsonSerialize.class);
                        // normal property

                        BeanPropertyWriter writer = new BeanIntrospectionPropertyWriter(
                            new IntrospectionVirtualAnnotatedMember(
                                typeResolutionContext,
                                beanProperty.getDeclaringType(),
                                propertyName,
                                newType(beanProperty.asArgument(), config.getTypeFactory()),
                                beanProperty
                            ),
                            config,
                            propertyName,
                            beanProperty,
                            config.getTypeFactory(),
                            serializerFromAnnotation
                            // would be nice to add the TypeSerializer here too, but we don't have access to the
                            // SerializerFactory for findPropertyTypeSerializer
                        );

                        if (bp.hasAnnotation(JsonAnyGetter.class)) {
                            AnnotatedMember virtualMember = new IntrospectionVirtualAnnotatedMember(
                                typeResolutionContext,
                                beanProperty.getDeclaringType(),
                                propertyName,
                                newType(beanProperty.asArgument(), config.getTypeFactory()),
                                beanProperty
                            ) {
                                @Override
                                public Object getValue(Object pojo) throws IllegalArgumentException {
                                    return beanProperty.get(pojo);
                                }
                            };
                            JavaType anyType = newType(beanProperty.asArgument(), config.getTypeFactory());
                            if (serializerFromAnnotation == null) {
                                serializerFromAnnotation = MapSerializer.construct(/* ignored props*/ (Set<String>) null,
                                    anyType, config.isEnabled(MapperFeature.USE_STATIC_TYPING),
                                    null, null, null, /*filterId*/ null);
                            }
                            AnyGetterWriter anyGetter = new AnyGetterWriter(
                                writer,
                                new com.fasterxml.jackson.databind.BeanProperty.Std(
                                    new PropertyName(bp.getName()),
                                    anyType.getContentType(),
                                    null,
                                    virtualMember,
                                    PropertyMetadata.STD_OPTIONAL
                                ),
                                virtualMember,
                                serializerFromAnnotation
                            );
                            newBuilder.setAnyGetter(anyGetter);
                        } else {
                            newProperties.add(writer);
                        }
                    }

                    if (newBuilder.getAnyGetter() != null) {
                        // place it last
                        newProperties.add(newBuilder.getAnyGetter());
                    }
                    newBuilder.setProperties(newProperties);
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Updating {} properties with BeanIntrospection data for type: {}", properties.size(), beanClass);
                    }

                    final var newProperties = new ArrayList<>(properties);
                    var named = new LinkedHashMap<String, BeanProperty<Object, Object>>();
                    for (BeanProperty<Object, Object> beanProperty : beanProperties) {
                        if (!beanProperty.isWriteOnly()) {
                            named.put(getName(config, namingStrategy, beanProperty), beanProperty);
                        }
                    }
                    for (int i = 0; i < properties.size(); i++) {
                        final BeanPropertyWriter existing = properties.get(i);

                        final Optional<BeanProperty<Object, Object>> property = Optional.ofNullable(named.get(existing.getName()));
                        // ignore properties that are @JsonIgnore, so that we don't replace other properties of the
                        // same name
                        if (property.isPresent() &&
                            !property.get().isAnnotationPresent(JsonIgnore.class) &&
                            // we can't support XmlBeanPropertyWriter easily https://github.com/micronaut-projects/micronaut-core/issues/5907
                            !existing.getClass().getName().equals("com.fasterxml.jackson.dataformat.xml.ser.XmlBeanPropertyWriter")
                            && !(existing instanceof AnyGetterWriter)) { // NOSONAR
                            final UnsafeBeanProperty<Object, Object> beanProperty = (UnsafeBeanProperty<Object, Object>) property.get();
                            newProperties.set(i, new BeanIntrospectionPropertyWriter(
                                    existing,
                                    beanProperty,
                                    existing.getSerializer(),
                                    config.getTypeFactory(),
                                    existing.getViews()
                                )
                            );
                        } else {
                            newProperties.set(i, existing);
                        }
                    }
                    newBuilder.setProperties(newProperties);
                }
                newBuilder.setFilteredProperties(builder.getFilteredProperties());
                return newBuilder;
            }
        }
    }

    /**
     * Modifies bean deserialization.
     */
    private class BeanIntrospectionDeserializerModifier extends BeanDeserializerModifier {

        @Override
        public BeanDeserializerBuilder updateBuilder(
            DeserializationConfig config,
            BeanDescription beanDesc,
            BeanDeserializerBuilder builder) {

            if (builder.getValueInstantiator().getDelegateType(config) != null) {
                return builder;
            }

            final Class<?> beanClass = beanDesc.getBeanClass();
            final var introspection = (UnsafeBeanInstantiationIntrospection<Object>) findIntrospection(beanClass);
            if (introspection == null) {
                return builder;
            } else {
                PropertyNamingStrategy propertyNamingStrategy = findNamingStrategy(config, introspection);

                final Iterator<SettableBeanProperty> properties = builder.getProperties();
                if ((ignoreReflectiveProperties || !properties.hasNext()) && introspection.getPropertyNames().length > 0) {
                    // mismatch, probably GraalVM reflection not enabled for bean. Try recreate
                    for (BeanProperty<Object, Object> beanProperty : introspection.getBeanProperties()) {
                        if (!beanProperty.isReadOnly()) {
                            builder.addOrReplaceProperty(new VirtualSetter(
                                    beanDesc.getClassInfo(),
                                    config.getTypeFactory(),
                                    (UnsafeBeanProperty<Object, Object>) beanProperty,
                                    getName(config, propertyNamingStrategy, beanProperty),
                                    findSerializerFromAnnotation(beanProperty, JsonDeserialize.class)),
                                true);
                        }
                    }
                } else {
                    var remainingProperties = new LinkedHashMap<String, BeanProperty<Object, Object>>();
                    for (BeanProperty<Object, Object> beanProperty : introspection.getBeanProperties()) {
                        // ignore properties that are @JsonIgnore, so that we don't replace other properties of the
                        // same name
                        if (beanProperty.isAnnotationPresent(JsonIgnore.class)) {
                            continue;
                        }

                        remainingProperties.put(getName(config, propertyNamingStrategy, beanProperty), beanProperty);
                    }
                    while (properties.hasNext()) {
                        final SettableBeanProperty settableBeanProperty = properties.next();
                        if (settableBeanProperty instanceof MethodProperty methodProperty) {
                            final UnsafeBeanProperty<Object, Object> beanProperty =
                                (UnsafeBeanProperty<Object, Object>) remainingProperties.remove(settableBeanProperty.getName());

                            if (beanProperty != null && !beanProperty.isReadOnly()) {
                                SettableBeanProperty newProperty = new BeanIntrospectionSetter(
                                    methodProperty,
                                    beanProperty
                                );
                                builder.addOrReplaceProperty(newProperty, true);
                            }
                        }
                    }
                    // add any remaining properties. This can happen if the supertype has reflection-visible properties
                    // so `properties` isn't empty, but the subtype doesn't have reflection enabled.
                    for (Map.Entry<String, BeanProperty<Object, Object>> entry : remainingProperties.entrySet()) {
                        if (!entry.getValue().isReadOnly()) {
                            SettableBeanProperty existing = builder.findProperty(PropertyName.construct(entry.getKey()));
                            if (existing == null) {
                                builder.addOrReplaceProperty(new VirtualSetter(
                                        beanDesc.getClassInfo(),
                                        config.getTypeFactory(),
                                        (UnsafeBeanProperty<Object, Object>) entry.getValue(),
                                        entry.getKey(),
                                        findSerializerFromAnnotation(entry.getValue(), JsonDeserialize.class)),
                                    true);
                            }
                        }
                    }
                }

                final Argument<?>[] constructorArguments = introspection.getConstructorArguments();
                final TypeFactory typeFactory = config.getTypeFactory();
                ValueInstantiator defaultInstantiator = builder.getValueInstantiator();
                builder.setValueInstantiator(new StdValueInstantiator(config, typeFactory.constructType(beanClass)) {
                    SettableBeanProperty[] props;

                    @Override
                    public SettableBeanProperty[] getFromObjectArguments(DeserializationConfig config) {

                        SettableBeanProperty[] existing = ignoreReflectiveProperties ? null : defaultInstantiator.getFromObjectArguments(config);
                        if (props == null) {
                            props = new SettableBeanProperty[constructorArguments.length];
                            for (int i = 0; i < constructorArguments.length; i++) {
                                Argument<?> argument = constructorArguments[i];
                                SettableBeanProperty existingProperty = existing != null && existing.length > i ? existing[i] : null;
                                final JavaType javaType = existingProperty != null ? existingProperty.getType() : newType(argument, typeFactory);
                                final AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
                                PropertyMetadata propertyMetadata = newPropertyMetadata(argument, annotationMetadata);
                                final String simpleName = existingProperty != null ? existingProperty.getName() : getName(config, propertyNamingStrategy, argument);
                                TypeDeserializer typeDeserializer;
                                try {
                                    typeDeserializer = config.findTypeDeserializer(javaType);
                                } catch (JsonMappingException e) {
                                    typeDeserializer = null;
                                }
                                PropertyName propertyName = PropertyName.construct(simpleName);
                                if (typeDeserializer == null) {
                                    SettableBeanProperty settableBeanProperty = builder.findProperty(propertyName);
                                    if (settableBeanProperty != null) {
                                        typeDeserializer = settableBeanProperty.getValueTypeDeserializer();
                                    }
                                }

                                props[i] = new CreatorProperty(
                                    propertyName,
                                    javaType,
                                    null,
                                    typeDeserializer,
                                    null,
                                    null,
                                    i,
                                    null,
                                    propertyMetadata

                                ) {
                                    private final UnsafeBeanProperty<Object, Object> property = (UnsafeBeanProperty<Object, Object>) introspection.getProperty(argument.getName()).orElse(null);

                                    @Override
                                    public <A extends Annotation> A getAnnotation(Class<A> acls) {
                                        return annotationMetadata.synthesize(acls);
                                    }

                                    @Override
                                    public AnnotatedMember getMember() {
                                        return new IntrospectionVirtualAnnotatedMember(beanDesc.getClassInfo(), beanClass, argument.getName(), javaType, annotationMetadata);
                                    }

                                    @Override
                                    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object instance) throws IOException {
                                        if (property != null) {
                                            property.setUnsafe(instance, deserialize(p, ctxt));
                                        }
                                    }

                                    @Override
                                    public Object deserializeSetAndReturn(JsonParser p, DeserializationContext ctxt, Object instance) throws IOException {
                                        if (property != null) {
                                            property.setUnsafe(instance, deserialize(p, ctxt));
                                        }
                                        return null;
                                    }

                                    @Override
                                    public void set(Object instance, Object value) {
                                        if (property != null) {
                                            property.setUnsafe(instance, value);
                                        }
                                    }

                                    @Override
                                    public Object setAndReturn(Object instance, Object value) {
                                        if (property != null) {
                                            property.setUnsafe(instance, value);
                                        }
                                        return null;
                                    }

                                    @Override
                                    public JsonFormat.Value findPropertyFormat(MapperConfig<?> config, Class<?> baseType) {
                                        JsonFormat.Value v1 = config.getDefaultPropertyFormat(baseType);
                                        JsonFormat.Value v2 = null;
                                        if (property != null) {
                                            AnnotationValue<JsonFormat> formatAnnotation = property.getAnnotation(JsonFormat.class);
                                            if (formatAnnotation != null) {
                                                v2 = parseJsonFormat(formatAnnotation);
                                            }
                                        }
                                        if (v1 == null) {
                                            return (v2 == null) ? EMPTY_FORMAT : v2;
                                        }
                                        return (v2 == null) ? v1 : v1.withOverrides(v2);
                                    }
                                };
                            }
                        }
                        return props;
                    }

                    @Override
                    public boolean canInstantiate() {
                        return true;
                    }

                    @Override
                    public boolean canCreateUsingDefault() {
                        return constructorArguments.length == 0;
                    }

                    @Override
                    public boolean canCreateFromObjectWith() {
                        return constructorArguments.length > 0;
                    }

                    @Override
                    public boolean canCreateUsingArrayDelegate() {
                        return defaultInstantiator.canCreateUsingArrayDelegate();
                    }

                    @Override
                    public boolean canCreateUsingDelegate() {
                        return false;
                    }

                    @Override
                    public JavaType getArrayDelegateType(DeserializationConfig config) {
                        return newType(constructorArguments[0], typeFactory);
                    }

                    @Override
                    public JavaType getDelegateType(DeserializationConfig config) {
                        return newType(constructorArguments[0], typeFactory);
                    }

                    @Override
                    public boolean canCreateFromString() {
                        return constructorArguments.length == 1 && constructorArguments[0].equalsType(Argument.STRING);
                    }

                    @Override
                    public boolean canCreateFromInt() {
                        return constructorArguments.length == 1 && (
                            constructorArguments[0].equalsType(Argument.INT) ||
                                constructorArguments[0].equalsType(Argument.LONG));
                    }

                    @Override
                    public boolean canCreateFromLong() {
                        return constructorArguments.length == 1 && constructorArguments[0].equalsType(Argument.LONG);
                    }

                    @Override
                    public boolean canCreateFromDouble() {
                        return constructorArguments.length == 1 && constructorArguments[0].equalsType(Argument.DOUBLE);
                    }

                    @Override
                    public boolean canCreateFromBoolean() {
                        return constructorArguments.length == 1 && constructorArguments[0].equalsType(Argument.BOOLEAN);
                    }

                    @Override
                    public Object createUsingDefault(DeserializationContext ctxt) {
                        return introspection.instantiate();
                    }

                    @Override
                    public Object createUsingDelegate(DeserializationContext ctxt, Object delegate) {
                        if (preInstantiateCallback != null) {
                            preInstantiateCallback.preInstantiate(introspection, delegate);
                        }
                        return introspection.instantiate(false, new Object[] { delegate });
                    }

                    @Override
                    public Object createFromObjectWith(DeserializationContext ctxt, Object[] args) {
                        if (preInstantiateCallback != null) {
                            preInstantiateCallback.preInstantiate(introspection, args);
                        }
                        return ((UnsafeBeanInstantiationIntrospection<?>) introspection).instantiateUnsafe(args);
                    }

                    @Override
                    public Object createUsingArrayDelegate(DeserializationContext ctxt, Object delegate) {
                        if (preInstantiateCallback != null) {
                            preInstantiateCallback.preInstantiate(introspection, delegate);
                        }
                        return introspection.instantiateUnsafe(new Object[] { delegate });                    }

                    @Override
                    public Object createFromString(DeserializationContext ctxt, String value) {
                        if (preInstantiateCallback != null) {
                            preInstantiateCallback.preInstantiate(introspection, value);
                        }
                        return introspection.instantiateUnsafe(new Object[]{ value });
                    }

                    @Override
                    public Object createFromInt(DeserializationContext ctxt, int value) {
                        if (preInstantiateCallback != null) {
                            preInstantiateCallback.preInstantiate(introspection, value);
                        }
                        InstantiationException originalException;
                        try {
                            return introspection.instantiate(false, new Object[]{value});
                        } catch (InstantiationException e) {
                            originalException = e;
                        }
                        try {
                            return introspection.instantiate(false, new Object[]{Long.valueOf(value)});
                        } catch (InstantiationException e) {
                            throw originalException;
                        }
                    }

                    @Override
                    public Object createFromLong(DeserializationContext ctxt, long value) {
                        if (preInstantiateCallback != null) {
                            preInstantiateCallback.preInstantiate(introspection, value);
                        }
                        return introspection.instantiateUnsafe(new Object[]{ value });
                    }

                    @Override
                    public Object createFromDouble(DeserializationContext ctxt, double value) {
                        if (preInstantiateCallback != null) {
                            preInstantiateCallback.preInstantiate(introspection, value);
                        }
                        return introspection.instantiateUnsafe(new Object[]{ value });
                    }

                    @Override
                    public Object createFromBoolean(DeserializationContext ctxt, boolean value) {
                        if (preInstantiateCallback != null) {
                            preInstantiateCallback.preInstantiate(introspection, value);
                        }
                        return introspection.instantiateUnsafe(new Object[]{ value });
                    }

                });
                return builder;
            }
        }
    }

    /**
     * A virtual property setter.
     */
    private class VirtualSetter extends SettableBeanProperty {

        final UnsafeBeanProperty<Object, Object> beanProperty;
        final TypeResolutionContext typeResolutionContext;

        VirtualSetter(TypeResolutionContext typeResolutionContext,
                      TypeFactory typeFactory,
                      UnsafeBeanProperty<Object, Object> beanProperty,
                      String propertyName,
                      JsonDeserializer<Object> valueDeser) {
            super(
                new PropertyName(propertyName),
                newType(beanProperty.asArgument(), typeFactory),
                newPropertyMetadata(beanProperty.asArgument(), beanProperty.getAnnotationMetadata()), valueDeser);
            this.beanProperty = beanProperty;
            this.typeResolutionContext = typeResolutionContext;
        }

        VirtualSetter(PropertyName propertyName, VirtualSetter src) {
            super(propertyName, src._type, src._metadata, src._valueDeserializer);
            this.beanProperty = src.beanProperty;
            this.typeResolutionContext = src.typeResolutionContext;
        }

        VirtualSetter(NullValueProvider nullValueProvider, VirtualSetter src) {
            super(src, src._valueDeserializer, nullValueProvider);
            this.beanProperty = src.beanProperty;
            this.typeResolutionContext = src.typeResolutionContext;
        }

        VirtualSetter(JsonDeserializer<Object> deser, VirtualSetter src) {
            super(src._propName, src._type, src._metadata, deser);
            this.beanProperty = src.beanProperty;
            this.typeResolutionContext = src.typeResolutionContext;
        }

        @Override
        public SettableBeanProperty withValueDeserializer(JsonDeserializer<?> deser) {
            return new VirtualSetter((JsonDeserializer<Object>) deser, this);
        }

        @Override
        public SettableBeanProperty withName(PropertyName newName) {
            return new VirtualSetter(newName, this);
        }

        @Override
        public SettableBeanProperty withNullProvider(NullValueProvider nva) {
            return new VirtualSetter(nva, this);
        }

        @Override
        public AnnotatedMember getMember() {
            return new IntrospectionVirtualAnnotatedMember(
                typeResolutionContext,
                beanProperty.getDeclaringType(),
                _propName.getSimpleName(),
                _type,
                beanProperty
            );
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> acls) {
            return beanProperty.getAnnotationMetadata().synthesize(acls);
        }

        @Override
        public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object instance) throws IOException {
            beanProperty.setUnsafe(instance, deserialize(p, ctxt));
        }

        @Override
        public Object deserializeSetAndReturn(JsonParser p, DeserializationContext ctxt, Object instance) throws IOException {
            beanProperty.setUnsafe(instance, deserialize(p, ctxt));
            return null;
        }

        @Override
        public void set(Object instance, Object value) {
            beanProperty.setUnsafe(instance, value);
        }

        @Override
        public Object setAndReturn(Object instance, Object value) {
            beanProperty.setUnsafe(instance, value);
            return null;
        }

        @Override
        public JsonFormat.Value findPropertyFormat(MapperConfig<?> config, Class<?> baseType) {
            JsonFormat.Value v1 = config.getDefaultPropertyFormat(baseType);
            JsonFormat.Value v2 = null;
            AnnotationValue<JsonFormat> formatAnnotation = beanProperty.getAnnotation(JsonFormat.class);
            if (formatAnnotation != null) {
                v2 = parseJsonFormat(formatAnnotation);
            }
            if (v1 == null) {
                return (v2 == null) ? EMPTY_FORMAT : v2;
            }
            return (v2 == null) ? v1 : v1.withOverrides(v2);
        }
    }


    /**
     * Introspected property writer.
     */
    private class BeanIntrospectionPropertyWriter extends BeanPropertyWriter {
        protected final Class<?>[] _views;
        final UnsafeBeanProperty<Object, Object> beanProperty;
        final SerializableString fastName;
        private final JavaType type;
        private final boolean unwrapping;

        BeanIntrospectionPropertyWriter(BeanPropertyWriter src,
                                        UnsafeBeanProperty<Object, Object> beanProperty,
                                        JsonSerializer<Object> ser,
                                        TypeFactory typeFactory,
                                        Class<?>[] views) {
            this(src.getSerializedName(), src, beanProperty, ser, typeFactory, views);
        }

        BeanIntrospectionPropertyWriter(SerializableString name,
                                        BeanPropertyWriter src,
                                        UnsafeBeanProperty<Object, Object> beanProperty,
                                        JsonSerializer<Object> ser,
                                        TypeFactory typeFactory,
                                        Class<?>[] views) {
            super(src);
            // either use the passed on serializer or the original one
            _serializer = (ser != null) ? ser : src.getSerializer();
            this.beanProperty = beanProperty;
            fastName = name;
            _views = views;
            this.type = JacksonConfiguration.constructType(this.beanProperty.asArgument(), typeFactory);
            _dynamicSerializers = (ser == null) ? PropertySerializerMap
                .emptyForProperties() : null;
            this.unwrapping = beanProperty.hasAnnotation(JsonUnwrapped.class);
        }

        BeanIntrospectionPropertyWriter(
            AnnotatedMember virtualMember,
            SerializationConfig config,
            String name,
            UnsafeBeanProperty<Object, Object> beanProperty,
            TypeFactory typeFactory,
            JsonSerializer<?> ser) {
            super(
                SimpleBeanPropertyDefinition.construct(config, virtualMember),
                virtualMember,
                AnnotationCollector.emptyAnnotations(),
                JacksonConfiguration.constructType(beanProperty.asArgument(), typeFactory),
                ser, null, null,
                suppressNulls(config.getDefaultPropertyInclusion()),
                suppressableValue(config.getDefaultPropertyInclusion()),
                null
            );
            this.type = super.getType();
            this.beanProperty = beanProperty;
            fastName = new SerializedString(name);
            _views = null;
            _dynamicSerializers = PropertySerializerMap
                .emptyForProperties();
            this.unwrapping = beanProperty.hasAnnotation(JsonUnwrapped.class);
        }

        @Override
        public boolean isUnwrapping() {
            return unwrapping;
        }

        @Override
        public String getName() {
            return fastName.getValue();
        }

        @Override
        public PropertyName getFullName() {
            return new PropertyName(getName());
        }

        @Override
        public void fixAccess(SerializationConfig config) {
            // no-op
        }

        @Override
        public JavaType getType() {
            return type;
        }

        private boolean inView(Class<?> activeView) {
            if (activeView == null || _views == null) {
                return true;
            }
            final int len = _views.length;
            for (int i = 0; i < len; ++i) {
                if (_views[i].isAssignableFrom(activeView)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public final void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
            if (!inView(prov.getActiveView())) {
                serializeAsOmittedField(bean, gen, prov);
                return;
            }
            Object value = beanProperty.get(bean);
            // Null (etc.) handling; copied from super-class impl
            if (value == null) {
                boolean willSuppressNulls = willSuppressNulls();
                if (!willSuppressNulls && _nullSerializer != null) {
                    if (!isUnwrapping()) {
                        gen.writeFieldName(fastName);
                        _nullSerializer.serialize(null, gen, prov);
                    }
                } else if (!willSuppressNulls) {
                    gen.writeFieldName(fastName);
                    prov.defaultSerializeNull(gen);
                }
                return;
            }
            JsonSerializer<Object> ser = _serializer;
            if (ser == null) {
                Class<?> cls = value.getClass();
                PropertySerializerMap map = _dynamicSerializers;
                ser = map.serializerFor(cls);
                if (ser == null) {
                    ser = _findAndAddDynamic(map, cls, prov);
                }
            }
            if (_suppressableValue != null) {
                if (MARKER_FOR_EMPTY == _suppressableValue) {
                    if (ser.isEmpty(prov, value)) {
                        return;
                    }
                } else if (_suppressableValue.equals(value)) {
                    return;
                }
            }
            if (value == bean && _handleSelfReference(bean, gen, prov, ser)) {
                // three choices: exception; handled by call; or pass-through
                return;
            }
            if (isUnwrapping()) {
                JsonSerializer<Object> unwrappingSerializer = ser.unwrappingSerializer(null);
                unwrappingSerializer.serialize(value, gen, prov);
            } else {
                gen.writeFieldName(fastName);
                if (_typeSerializer == null) {
                    ser.serialize(value, gen, prov);
                } else {
                    ser.serializeWithType(value, gen, prov, _typeSerializer);
                }
            }
        }

        @Override
        public final void serializeAsElement(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
            if (!inView(prov.getActiveView())) {
                serializeAsOmittedField(bean, gen, prov);
                return;
            }

            Object value = beanProperty.get(bean);
            // Null (etc.) handling; copied from super-class impl
            if (value == null) {
                boolean willSuppressNulls = willSuppressNulls();
                if (!willSuppressNulls && _nullSerializer != null) {
                    _nullSerializer.serialize(null, gen, prov);
                } else if (willSuppressNulls) {
                    serializeAsPlaceholder(bean, gen, prov);
                } else {
                    prov.defaultSerializeNull(gen);
                }
                return;
            }
            JsonSerializer<Object> ser = _serializer;
            if (ser == null) {
                Class<?> cls = value.getClass();
                PropertySerializerMap map = _dynamicSerializers;
                ser = map.serializerFor(cls);
                if (ser == null) {
                    ser = _findAndAddDynamic(map, cls, prov);
                }
            }
            if (_suppressableValue != null) {
                if (MARKER_FOR_EMPTY == _suppressableValue) {
                    if (ser.isEmpty(prov, value)) {
                        serializeAsPlaceholder(bean, gen, prov);
                        return;
                    }
                } else if (_suppressableValue.equals(value)) {
                    serializeAsPlaceholder(bean, gen, prov);
                    return;
                }
            }
            if (value == bean && _handleSelfReference(bean, gen, prov, ser)) {
                // three choices: exception; handled by call; or pass-through
                return;
            }
            if (_typeSerializer == null) {
                ser.serialize(value, gen, prov);
            } else {
                ser.serializeWithType(value, gen, prov, _typeSerializer);
            }
        }

        @Override
        public JsonFormat.Value findPropertyFormat(MapperConfig<?> config, Class<?> baseType) {
            JsonFormat.Value v1 = config.getDefaultPropertyFormat(baseType);
            JsonFormat.Value v2 = null;
            AnnotationValue<JsonFormat> formatAnnotation = beanProperty.getAnnotation(JsonFormat.class);
            if (formatAnnotation != null) {
                v2 = parseJsonFormat(formatAnnotation);
            }
            if (v1 == null) {
                return (v2 == null) ? EMPTY_FORMAT : v2;
            }
            return (v2 == null) ? v1 : v1.withOverrides(v2);
        }
    }

    /**
     * A bean introspection setter.
     */
    private static class BeanIntrospectionSetter extends SettableBeanProperty.Delegating {

        final UnsafeBeanProperty<Object, Object> beanProperty;

        BeanIntrospectionSetter(SettableBeanProperty methodProperty, UnsafeBeanProperty<Object, Object> beanProperty) {
            super(methodProperty);
            this.beanProperty = beanProperty;
        }

        @Override
        protected SettableBeanProperty withDelegate(SettableBeanProperty d) {
            return new BeanIntrospectionSetter(d, beanProperty);
        }

        @Override
        public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object instance) throws IOException {
            beanProperty.setUnsafe(instance, deserialize(p, ctxt));
        }

        @Override
        public Object deserializeSetAndReturn(JsonParser p, DeserializationContext ctxt, Object instance) throws IOException {
            beanProperty.setUnsafe(instance, deserialize(p, ctxt));
            return null;
        }

        @Override
        public void set(Object instance, Object value) {
            beanProperty.setUnsafe(instance, value);
        }

        @Override
        public Object setAndReturn(Object instance, Object value) {
            beanProperty.setUnsafe(instance, value);
            return null;
        }
    }

    private class BeanIntrospectionAccessorNamingStrategyProvider extends AccessorNamingStrategy.Provider {
        private final AccessorNamingStrategy.Provider delegate;

        BeanIntrospectionAccessorNamingStrategyProvider(AccessorNamingStrategy.Provider delegate) {
            this.delegate = delegate;
        }

        @Override
        public AccessorNamingStrategy forPOJO(MapperConfig<?> config, AnnotatedClass valueClass) {
            return delegate.forPOJO(config, valueClass);
        }

        @Override
        public AccessorNamingStrategy forBuilder(MapperConfig<?> config, AnnotatedClass builderClass, BeanDescription valueTypeDesc) {
            return delegate.forBuilder(config, builderClass, valueTypeDesc);
        }

        @Override
        public AccessorNamingStrategy forRecord(MapperConfig<?> config, AnnotatedClass recordClass) {
            BeanIntrospection<Object> introspection = findIntrospection(recordClass.getRawType());
            if (introspection != null) {
                return new DefaultAccessorNamingStrategy(config, recordClass, null, "get", "is", null) {
                    final Set<String> names = introspection.getBeanProperties().stream().map(BeanProperty::getName).collect(Collectors.toSet());

                    @Override
                    public String findNameForRegularGetter(AnnotatedMethod am, String name) {
                        if (names.contains(name)) {
                            return name;
                        }
                        return super.findNameForRegularGetter(am, name);
                    }
                };
            } else {
                try {
                    return delegate.forRecord(config, recordClass);
                } catch (IllegalArgumentException e) {
                    if (e.getMessage().startsWith("Failed to access RecordComponents of type")) {
                        throw new RuntimeException("Failed to construct AccessorNamingStrategy for record. This can happen when running in native-image. Either make this type @Introspected, or mark it for @ReflectiveAccess.", e);
                    } else {
                        throw e;
                    }
                }
            }
        }
    }
}
