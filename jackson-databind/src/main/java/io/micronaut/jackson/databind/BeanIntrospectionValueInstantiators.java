/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.jackson.databind;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.UnsafeBeanInstantiationIntrospection;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.Version;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.ValueInstantiator;
import tools.jackson.databind.deser.ValueInstantiators;
import tools.jackson.databind.deser.std.StdValueInstantiator;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.AnnotatedConstructor;
import tools.jackson.databind.introspect.AnnotatedWithParams;

import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * Lets Jackson create beans through their {@link BeanIntrospection} when Jackson uses the constructor of
 * the introspection as the properties-based creator.
 *
 * <p>Jackson invokes a properties-based creator with {@code MethodHandle.invokeWithArguments}, which builds a
 * spreader handle on every call. The introspection calls the constructor directly from generated code, so
 * records and other constructor-bound types avoid that cost. Only
 * {@link ValueInstantiator#createFromObjectWith(DeserializationContext, Object[])} changes: the arguments
 * Jackson collected, including defaults for missing properties, are passed unchanged, and an exception
 * thrown by the constructor is reported exactly as Jackson reports it.</p>
 *
 * <p>Jackson keeps its own instantiator unless the default {@link StdValueInstantiator} uses a constructor
 * that is the constructor of the introspection, as reported by
 * {@link io.micronaut.core.beans.BeanConstructor#getTargetConstructor()}, and the generated introspection
 * calls it without reflection. Factory methods, static creator methods, private constructors, introspections
 * built through a builder, Kotlin classes, instantiators customized by other modules and constructors the
 * introspection does not describe all stay with Jackson. For a type other than a record, a call with a
 * {@code null} argument also stays with Jackson, since the generated code may replace it with a default
 * declared for the parameter.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class BeanIntrospectionValueInstantiators implements ValueInstantiators {

    private static final String KOTLIN_METADATA = "kotlin.Metadata";

    private final BeanIntrospector beanIntrospector;

    /**
     * @param beanIntrospector The introspector used to find the introspections
     */
    public BeanIntrospectionValueInstantiators(BeanIntrospector beanIntrospector) {
        this.beanIntrospector = beanIntrospector;
    }

    /**
     * Creates a module that registers the instantiators.
     *
     * @param beanIntrospector The introspector used to find the introspections
     * @return The module
     */
    public static JacksonModule module(BeanIntrospector beanIntrospector) {
        return new Module(new BeanIntrospectionValueInstantiators(beanIntrospector));
    }

    @Override
    public @Nullable ValueInstantiator findValueInstantiator(DeserializationConfig config, BeanDescription.Supplier beanDescRef) {
        return null;
    }

    @Override
    public ValueInstantiator modifyValueInstantiator(DeserializationConfig config,
                                                     BeanDescription.Supplier beanDescRef,
                                                     ValueInstantiator defaultInstantiator) {
        // Only the plain Jackson instantiator: subclasses (Kotlin, Afterburner, ...) have their own logic
        if (defaultInstantiator.getClass() != StdValueInstantiator.class) {
            return defaultInstantiator;
        }
        StdValueInstantiator instantiator = (StdValueInstantiator) defaultInstantiator;
        if (!instantiator.canCreateFromObjectWith()
            || !(instantiator.getWithArgsCreator() instanceof AnnotatedConstructor constructor)) {
            return defaultInstantiator;
        }
        Class<?> beanClass = beanDescRef.getBeanClass();
        if (isKotlin(beanDescRef.getClassInfo())) {
            // Kotlin constructors may substitute declared defaults, leave them to Jackson
            return defaultInstantiator;
        }
        UnsafeBeanInstantiationIntrospection<?> introspection = findIntrospection(beanClass, constructor);
        if (introspection == null) {
            return defaultInstantiator;
        }
        SettableBeanProperty[] creatorProperties = instantiator.getFromObjectArguments(config);
        if (creatorProperties == null || creatorProperties.length != constructor.getParameterCount()) {
            return defaultInstantiator;
        }
        return new IntrospectedValueInstantiator(instantiator, introspection, beanClass.isRecord());
    }

    private static boolean isKotlin(AnnotatedClass classInfo) {
        return classInfo.annotations().anyMatch(a -> KOTLIN_METADATA.equals(a.annotationType().getName()));
    }

    private @Nullable UnsafeBeanInstantiationIntrospection<?> findIntrospection(Class<?> beanClass, AnnotatedConstructor constructor) {
        if (constructor.getDeclaringClass() != beanClass || constructor.getParameterCount() == 0) {
            return null;
        }
        int modifiers = constructor.getMember().getModifiers();
        if (Modifier.isPrivate(modifiers)) {
            // the introspection would call it through reflection, which wraps exceptions differently
            return null;
        }
        Optional<? extends BeanIntrospection<?>> found = beanIntrospector.findIntrospection(beanClass);
        if (found.isEmpty()
            || !(found.get() instanceof UnsafeBeanInstantiationIntrospection<?> introspection)
            || introspection.getBeanType() != beanClass
            || introspection.hasBuilder()) {
            return null;
        }
        if (!introspection.getClass().getPackageName().equals(beanClass.getPackageName())
            && !(Modifier.isPublic(modifiers) && Modifier.isPublic(beanClass.getModifiers()))) {
            // not accessible from the generated introspection without reflection
            return null;
        }
        Argument<?>[] arguments = introspection.getConstructorArguments();
        if (arguments.length != constructor.getParameterCount()) {
            return null;
        }
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i].getType() != constructor.getRawParameterType(i)) {
                return null;
            }
        }
        // null for a static creator method, even one sharing the parameter types of the constructor
        if (!constructor.getAnnotated().equals(introspection.getConstructor().getTargetConstructor())) {
            return null;
        }
        return introspection;
    }

    /**
     * A {@link StdValueInstantiator} that creates the bean through the introspection.
     */
    static final class IntrospectedValueInstantiator extends StdValueInstantiator {

        private final UnsafeBeanInstantiationIntrospection<?> introspection;
        private final boolean[] primitiveParameters;
        private final boolean hasPrimitiveParameters;
        private final boolean acceptsNulls;

        IntrospectedValueInstantiator(StdValueInstantiator src,
                                      UnsafeBeanInstantiationIntrospection<?> introspection,
                                      boolean acceptsNulls) {
            super(src);
            this.introspection = introspection;
            this.acceptsNulls = acceptsNulls;
            AnnotatedWithParams creator = src.getWithArgsCreator();
            int count = creator.getParameterCount();
            primitiveParameters = new boolean[count];
            boolean hasPrimitives = false;
            for (int i = 0; i < count; i++) {
                boolean primitive = creator.getRawParameterType(i).isPrimitive();
                primitiveParameters[i] = primitive;
                hasPrimitives |= primitive;
            }
            hasPrimitiveParameters = hasPrimitives;
        }

        @Override
        public Object createFromObjectWith(DeserializationContext ctxt, Object[] args) throws JacksonException {
            if (args.length != primitiveParameters.length || hasUnsupportedNull(args)) {
                return super.createFromObjectWith(ctxt, args);
            }
            try {
                return introspection.instantiateUnsafe(args);
            } catch (Exception e) {
                return ctxt.handleInstantiationProblem(_valueClass, args, rewrapCtorProblem(ctxt, e));
            }
        }

        /**
         * A {@code null} for a primitive is left to Jackson to report. For a type other than a record, the
         * generated introspection may replace a {@code null} with a default the language declares for the
         * parameter, so Jackson calls the constructor itself whenever an argument is {@code null}.
         */
        private boolean hasUnsupportedNull(Object[] args) {
            if (acceptsNulls && !hasPrimitiveParameters) {
                return false;
            }
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null && (!acceptsNulls || primitiveParameters[i])) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * The module registering the instantiators.
     */
    private static final class Module extends JacksonModule {

        private final BeanIntrospectionValueInstantiators instantiators;

        Module(BeanIntrospectionValueInstantiators instantiators) {
            this.instantiators = instantiators;
        }

        @Override
        public String getModuleName() {
            return "micronaut-bean-introspection-instantiators";
        }

        @Override
        public Version version() {
            return Version.unknownVersion();
        }

        @Override
        public void setupModule(SetupContext context) {
            context.addValueInstantiators(instantiators);
        }
    }
}
