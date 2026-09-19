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
package io.micronaut.context.python.runtime;

import io.micronaut.context.python.runtime.codec.PythonMetadataCodec;
import io.micronaut.context.python.runtime.codec.PythonMetadataFormatException;
import io.micronaut.context.python.runtime.generate.PythonMetadataClassGenerator;
import io.micronaut.context.python.runtime.model.ClassModel;
import io.micronaut.context.python.runtime.model.PythonMetadataModel;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.inject.BeanDefinitionReference;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates, defines and caches the metadata classes of Python wrappers at runtime.
 *
 * <p>Each wrapper class owns one holder, kept in a {@link ClassValue} so that it lives and dies with the class and its
 * loader. The holder serializes the first generation of each artifact: the definition class is defined once, in the
 * wrapper's loader and runtime package, and a failure after that point is recorded and reported again on every later
 * request instead of defining the class a second time. The generated definition class produces a new definition
 * instance per {@link BeanDefinitionReference#load()}, so context-dependent state never leaves a context; the
 * introspection is context-free and is instantiated once.</p>
 *
 * @since 5.3.0
 */
@Internal
public final class PythonRuntimeMetadata {

    private static final ClassValue<Holder> HOLDERS = new ClassValue<>() {
        @Override
        protected Holder computeValue(Class<?> type) {
            // ClassValue may compute competing holders; none defines a class until it is the one returned and used.
            return new Holder(type);
        }
    };
    private static final AtomicLong GENERATED_CLASSES = new AtomicLong();

    private PythonRuntimeMetadata() {
    }

    static Holder holder(Class<?> beanType) {
        return HOLDERS.get(beanType);
    }

    /**
     * Reads the saved model of a class.
     *
     * @param beanType The wrapper class
     * @return The model
     * @throws PythonMetadataFormatException If the model is missing, corrupt or mismatched
     */
    public static PythonMetadataModel model(Class<?> beanType) {
        return holder(beanType).model();
    }

    /**
     * Reads the saved model of a class if it has one.
     *
     * @param beanType The wrapper class
     * @return The model, or empty when the class has no saved model
     */
    public static Optional<PythonMetadataModel> findModel(Class<?> beanType) {
        ClassLoader loader = beanType.getClassLoader();
        if (loader == null || loader.getResource(PythonMetadataCodec.modelResource(beanType.getName())) == null) {
            return Optional.empty();
        }
        return Optional.of(model(beanType));
    }

    /**
     * The class annotation metadata of a wrapper, materialized from its model without generating anything.
     *
     * @param beanType The wrapper class
     * @return The annotation metadata
     */
    public static AnnotationMetadata annotationMetadata(Class<?> beanType) {
        return holder(beanType).annotationMetadata();
    }

    /**
     * @param beanType The wrapper class
     * @return The identity of the saved model
     */
    public static String modelIdentity(Class<?> beanType) {
        Holder holder = holder(beanType);
        holder.model();
        return Objects.requireNonNull(holder.identity());
    }

    /**
     * Generates the definition class if needed and returns a new definition, as the reference of the class would.
     *
     * @param beanType The wrapper class
     * @return A new definition
     */
    @SuppressWarnings("unchecked")
    public static BeanDefinitionReference<Object> definition(Class<?> beanType) {
        Holder holder = holder(beanType);
        Class<?> definitionClass = holder.definitionClass();
        try {
            return (BeanDefinitionReference<Object>) MethodHandles.privateLookupIn(beanType, MethodHandles.lookup())
                .findConstructor(definitionClass, MethodType.methodType(void.class)).invoke();
        } catch (Throwable e) {
            throw new PythonMetadataGenerationException(beanType, holder.identity(), "instantiating " + definitionClass.getName(), e);
        }
    }

    /**
     * Generates the introspection class if needed and returns its shared instance.
     *
     * @param beanType The wrapper class
     * @return The introspection
     */
    public static BeanIntrospection<Object> introspection(Class<?> beanType) {
        return holder(beanType).introspection();
    }

    /**
     * @param beanType The wrapper class
     * @return Whether the definition class of the wrapper has been generated
     */
    public static boolean isDefinitionGenerated(Class<?> beanType) {
        return holder(beanType).definitionClass != null;
    }

    /**
     * @param beanType The wrapper class
     * @return Whether the introspection of the wrapper has been generated
     */
    public static boolean isIntrospectionGenerated(Class<?> beanType) {
        return holder(beanType).introspection != null;
    }

    /**
     * @return How many classes this runtime has defined so far
     */
    public static long generatedClassCount() {
        return GENERATED_CLASSES.get();
    }

    /**
     * Generates the bytes of the definition class of a model, as both backends do.
     *
     * @param model The class model
     * @return The class bytes
     */
    public static byte[] definitionBytes(ClassModel model) {
        return PythonMetadataClassGenerator.beanDefinition(model);
    }

    /**
     * Generates the bytes of the introspection class of a model, as both backends do.
     *
     * @param model The class model
     * @return The class bytes
     */
    public static byte[] introspectionBytes(ClassModel model) {
        return PythonMetadataClassGenerator.introspection(model);
    }

    static final class Holder {
        private final Class<?> beanType;
        private volatile @Nullable PythonMetadataModel model;
        private volatile @Nullable String identity;
        private volatile @Nullable AnnotationMetadata annotationMetadata;
        private volatile @Nullable PythonDefinitionState definitionState;
        private volatile @Nullable PythonIntrospectionState introspectionState;
        private volatile @Nullable Class<?> definitionClass;
        private volatile @Nullable Throwable definitionFailure;
        private volatile @Nullable BeanIntrospection<Object> introspection;
        private volatile @Nullable Throwable introspectionFailure;

        private Holder(Class<?> beanType) {
            this.beanType = beanType;
        }

        synchronized PythonMetadataModel model() {
            PythonMetadataModel current = model;
            if (current == null) {
                String resource = PythonMetadataCodec.modelResource(beanType.getName());
                ClassLoader loader = beanType.getClassLoader();
                URL url = loader == null ? null : loader.getResource(resource);
                if (url == null) {
                    throw new PythonMetadataFormatException(resource, "no saved model for " + beanType.getName() + " in " + loader, null);
                }
                byte[] bytes;
                try (InputStream stream = url.openStream()) {
                    bytes = stream.readAllBytes();
                } catch (IOException e) {
                    throw new PythonMetadataFormatException(resource, "cannot read " + url + ": " + e.getMessage(), e);
                }
                current = PythonMetadataCodec.decode(bytes, url.toString());
                if (!current.classModel().className().equals(beanType.getName())) {
                    throw new PythonMetadataFormatException(url.toString(), "describes " + current.classModel().className()
                        + " but was found for " + beanType.getName(), null);
                }
                identity = PythonMetadataCodec.identity(bytes);
                model = current;
            }
            return current;
        }

        @Nullable String identity() {
            return identity;
        }

        /**
         * The class annotation metadata alone, for discovery: no member type is loaded and nothing is generated.
         */
        synchronized AnnotationMetadata annotationMetadata() {
            AnnotationMetadata current = annotationMetadata;
            if (current == null) {
                current = new ModelMaterializer(beanType.getClassLoader()).annotationMetadata(model().classModel().annotationMetadata());
                annotationMetadata = current;
            }
            return current;
        }

        synchronized PythonDefinitionState definitionState() {
            PythonDefinitionState state = definitionState;
            if (state == null) {
                state = PythonDefinitionState.of(beanType, model().classModel());
                definitionState = state;
            }
            return state;
        }

        synchronized PythonIntrospectionState introspectionState() {
            PythonIntrospectionState state = introspectionState;
            if (state == null) {
                try {
                    state = PythonIntrospectionState.of(beanType, model().classModel());
                } catch (ClassNotFoundException e) {
                    throw new PythonMetadataGenerationException(beanType, identity, "materializing the introspection state", e);
                }
                introspectionState = state;
            }
            return state;
        }

        synchronized Class<?> definitionClass() {
            Class<?> generated = definitionClass;
            if (generated != null) {
                return generated;
            }
            if (definitionFailure != null) {
                throw new PythonMetadataGenerationException(beanType, identity, "an earlier definition generation", definitionFailure);
            }
            ClassModel classModel = model().classModel();
            if (classModel.beanDefinition() == null) {
                throw new PythonMetadataGenerationException(beanType, identity, "generating the definition",
                    new IllegalStateException("the model has no bean definition"));
            }
            try {
                generated = define(PythonMetadataClassGenerator.beanDefinition(classModel), "definition");
                definitionClass = generated;
                return generated;
            } catch (RuntimeException | Error e) {
                definitionFailure = e;
                throw e;
            }
        }

        synchronized BeanIntrospection<Object> introspection() {
            BeanIntrospection<Object> current = introspection;
            if (current != null) {
                return current;
            }
            if (introspectionFailure != null) {
                throw new PythonMetadataGenerationException(beanType, identity, "an earlier introspection generation", introspectionFailure);
            }
            ClassModel classModel = model().classModel();
            if (classModel.introspection() == null) {
                throw new PythonMetadataGenerationException(beanType, identity, "generating the introspection",
                    new IllegalStateException("the model has no introspection"));
            }
            try {
                Class<?> generated = define(PythonMetadataClassGenerator.introspection(classModel), "introspection");
                try {
                    @SuppressWarnings("unchecked")
                    BeanIntrospection<Object> instance = (BeanIntrospection<Object>) MethodHandles.privateLookupIn(beanType, MethodHandles.lookup())
                        .findConstructor(generated, MethodType.methodType(void.class)).invoke();
                    current = instance;
                } catch (Throwable e) {
                    throw new PythonMetadataGenerationException(beanType, identity, "instantiating " + generated.getName(), e);
                }
                introspection = current;
                return current;
            } catch (RuntimeException | Error e) {
                introspectionFailure = e;
                throw e;
            }
        }

        private Class<?> define(byte[] bytes, String what) {
            MethodHandles.Lookup lookup;
            try {
                lookup = MethodHandles.privateLookupIn(beanType, MethodHandles.lookup());
            } catch (IllegalAccessException e) {
                throw new PythonMetadataGenerationException(beanType, identity, "obtaining a lookup for the " + what, e);
            }
            Class<?> generated;
            try {
                generated = lookup.defineClass(bytes);
                GENERATED_CLASSES.incrementAndGet();
            } catch (IllegalAccessException | LinkageError e) {
                throw new PythonMetadataGenerationException(beanType, identity, "defining the " + what + " class", e);
            }
            try {
                // Initialize now, while this holder is locked: the static initializer reads the state through this holder.
                lookup.ensureInitialized(generated);
            } catch (IllegalAccessException | LinkageError e) {
                throw new PythonMetadataGenerationException(beanType, identity, "initializing " + generated.getName(), e);
            }
            return generated;
        }
    }
}
