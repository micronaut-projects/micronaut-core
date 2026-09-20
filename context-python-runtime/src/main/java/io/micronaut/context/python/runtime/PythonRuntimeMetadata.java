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
import io.micronaut.context.python.runtime.model.BeanDefinitionModel;
import io.micronaut.context.python.runtime.model.ClassModel;
import io.micronaut.context.python.runtime.model.PythonMetadataModel;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.util.NativeImageUtils;
import io.micronaut.inject.BeanDefinitionReference;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
     * Generates a definition class if needed and returns a new definition, as the reference of the class would.
     *
     * @param owner               The class the model describes: the bean, or the factory producing it
     * @param definitionClassName The definition class name
     * @return A new definition
     */
    @SuppressWarnings("unchecked")
    public static BeanDefinitionReference<Object> definition(Class<?> owner, String definitionClassName) {
        Holder holder = holder(owner);
        Class<?> definitionClass = holder.definitionClass(definitionClassName);
        try {
            return (BeanDefinitionReference<Object>) MethodHandles.privateLookupIn(owner, MethodHandles.lookup())
                .findConstructor(definitionClass, MethodType.methodType(void.class)).invoke();
        } catch (Throwable e) {
            throw new PythonMetadataGenerationException(owner, holder.identity(), "instantiating " + definitionClass.getName(), e);
        }
    }

    /**
     * The annotation metadata of a definition, materialized from the model without generating anything.
     *
     * @param owner      The class the model describes
     * @param definition The definition
     * @return The annotation metadata
     */
    public static AnnotationMetadata definitionAnnotationMetadata(Class<?> owner, BeanDefinitionModel definition) {
        return holder(owner).definitionAnnotationMetadata(definition);
    }

    /**
     * Whether a definition declares an annotation, answered from the saved model without generating anything.
     *
     * @param owner          The class the model describes
     * @param definition     The definition
     * @param annotationName The annotation name
     * @return Whether it is declared
     */
    public static boolean declares(Class<?> owner, BeanDefinitionModel definition, String annotationName) {
        return holder(owner).model().classModel().declares(definition, annotationName);
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
     * @return Whether the definition class of the wrapper itself (not of the beans it may produce) has been generated
     */
    public static boolean isDefinitionGenerated(Class<?> beanType) {
        Holder holder = holder(beanType);
        for (Holder.Definition definition : holder.definitions.values()) {
            if (definition.definitionClass != null && definition.model.factory() == null) {
                return true;
            }
        }
        return false;
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
     * Generates the bytes of a definition class of a model, as both backends do.
     *
     * @param model      The class model
     * @param definition The definition, one of the model's
     * @return The class bytes
     */
    public static byte[] definitionBytes(ClassModel model, BeanDefinitionModel definition) {
        return PythonMetadataClassGenerator.beanDefinition(model, definition);
    }

    /**
     * Generates the bytes of the executable methods definition class of a definition, as both backends do.
     *
     * @param model      The class model
     * @param definition The definition, one of the model's, which must have executable methods
     * @return The class bytes
     */
    public static byte[] executableMethodsBytes(ClassModel model, BeanDefinitionModel definition) {
        return PythonMetadataClassGenerator.executableMethods(model, definition);
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
        private final Map<String, Definition> definitions = new ConcurrentHashMap<>();
        private volatile @Nullable PythonIntrospectionState introspectionState;
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

        private Definition definition(String definitionClassName) {
            return definitions.computeIfAbsent(definitionClassName, name -> new Definition(model().classModel().definition(name)));
        }

        synchronized PythonDefinitionState definitionState(String definitionClassName) {
            Definition definition = definition(definitionClassName);
            PythonDefinitionState state = definition.state;
            if (state == null) {
                state = PythonDefinitionState.of(beanType, model().classModel(), definition.model);
                definition.state = state;
            }
            return state;
        }

        synchronized AnnotationMetadata definitionAnnotationMetadata(BeanDefinitionModel definitionModel) {
            Definition definition = definition(definitionModel.definitionClassName());
            AnnotationMetadata current = definition.annotationMetadata;
            if (current == null) {
                current = definitionModel.annotationMetadata() == null ? annotationMetadata()
                    : PythonDefinitionState.annotationMetadata(new ModelMaterializer(beanType.getClassLoader()), annotationMetadata(), definitionModel);
                definition.annotationMetadata = current;
            }
            return current;
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

        synchronized Class<?> definitionClass(String definitionClassName) {
            Definition definition = definition(definitionClassName);
            Class<?> generated = definition.definitionClass;
            if (generated != null) {
                return generated;
            }
            if (definition.failure != null) {
                throw new PythonMetadataGenerationException(beanType, identity, "an earlier generation of " + definitionClassName, definition.failure);
            }
            ClassModel classModel = model().classModel();
            try {
                if (!definition.model.executableMethods().isEmpty()) {
                    // the definition's static initializer instantiates its executable methods companion
                    define(PythonMetadataClassGenerator.executableMethods(classModel, definition.model), "executable methods definition " + definitionClassName);
                }
                generated = define(PythonMetadataClassGenerator.beanDefinition(classModel, definition.model), "definition " + definitionClassName);
                definition.definitionClass = generated;
                return generated;
            } catch (RuntimeException | Error e) {
                definition.failure = e;
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
            if (NativeImageUtils.inImageRuntimeCode()) {
                // A native image holds the classes decided when it was built, and defines none while it runs
                throw new PythonMetadataGenerationException(beanType, identity, "defining the " + what
                    + " class in a native image, which generates no class while it runs; build the image with"
                    + " micronaut.python.metadata.backend=model-build-time, which writes the same classes at compile time", null);
            }
            MethodHandles.Lookup lookup;
            try {
                lookup = MethodHandles.privateLookupIn(beanType, MethodHandles.lookup());
            } catch (IllegalAccessException e) {
                // On the module path the package of the class has to be open to this module for a class to be
                // defined next to it
                throw new PythonMetadataGenerationException(beanType, identity, "obtaining a lookup for the " + what
                    + " (open " + beanType.getPackageName() + " to io.micronaut.context.python.runtime, or compile with"
                    + " micronaut.python.metadata.backend=model-build-time)", e);
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

        /**
         * One definition of the class: its state, its generated class, or the failure that stopped its generation.
         */
        static final class Definition {
            private final BeanDefinitionModel model;
            private volatile @Nullable AnnotationMetadata annotationMetadata;
            private volatile @Nullable PythonDefinitionState state;
            private volatile @Nullable Class<?> definitionClass;
            private volatile @Nullable Throwable failure;

            private Definition(BeanDefinitionModel model) {
                this.model = model;
            }
        }
    }
}
