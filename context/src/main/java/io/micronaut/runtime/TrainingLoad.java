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
package io.micronaut.runtime;

import io.micronaut.context.AbstractInitializableBeanDefinitionAndReference;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.ConfigurableBeanContext;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.type.Argument;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.AdvisedBeanType;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.MethodInjectionPoint;
import io.micronaut.inject.ProxyBeanDefinition;
import io.micronaut.inject.qualifiers.PrimaryQualifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The {@code load} mode of a training run ({@link ApplicationConfiguration#TRAINING_MODE}): it
 * loads the enabled bean definitions and the classes they name, creates no bean and does not start
 * the application, so the run needs none of the services the application uses, such as a database.
 *
 * <p>It goes as far as {@link ConfigurableBeanContext#configure()}: the environment is started, the
 * bean configurations and the bean definition references are read and their conditions are
 * evaluated. Nothing of what {@link ApplicationContext#start()} does after that runs: no type
 * converter, eager or parallel bean is created, no method is processed and no startup event is
 * published.</p>
 *
 * <p>{@link Micronaut#start()} only refers to this class once the training run switch is on, so an
 * application that is not training never loads it.</p>
 *
 * @since 5.3.0
 */
@Internal
final class TrainingLoad {

    static final String MODE_START = "start";
    static final String MODE_LOAD = "load";

    // The messages of a training run come from one logger, whatever the mode
    private static final Logger LOG = LoggerFactory.getLogger(Micronaut.class);
    private static final String WARMUP_PREFIX = ApplicationConfiguration.PREFIX + ".training.warmup";

    private TrainingLoad() {
    }

    /**
     * Reads {@link ApplicationConfiguration#TRAINING_MODE}, in any case. Only called when the
     * training run switch is on.
     *
     * @param environment The started environment
     * @return Whether the training run loads the bean definitions instead of starting the application
     * @throws ConfigurationException if the mode is neither {@code start} nor {@code load}
     */
    static boolean isSelected(Environment environment) {
        String mode = environment.getProperty(ApplicationConfiguration.TRAINING_MODE, String.class).orElse(null);
        if (mode == null || mode.isBlank() || MODE_START.equalsIgnoreCase(mode.trim())) {
            return false;
        }
        if (MODE_LOAD.equalsIgnoreCase(mode.trim())) {
            return true;
        }
        // A mistyped mode must not turn a run that was meant to need no database into one that starts the application
        throw new ConfigurationException("Unknown training mode [" + mode + "]: " + ApplicationConfiguration.TRAINING_MODE
            + " must be " + MODE_START + " or " + MODE_LOAD);
    }

    /**
     * Runs the training run in {@code load} mode: announces it, loads the bean definitions, closes
     * the context and exits with status 0, except in the {@code test} environment.
     *
     * @param applicationContext The application context, built and not started, with a started environment
     */
    @SuppressWarnings("java:S1147") // Exiting the JVM is the point of a training run
    static void run(ApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();
        boolean exit = !environment.getActiveNames().contains(Environment.TEST);
        if (LOG.isWarnEnabled()) {
            LOG.warn("Training run ({}=true, {}=load): this JVM is a training run and does not serve traffic. "
                    + "It loads the enabled bean definitions and the classes they name, creates no bean and does not start the application{}. "
                    + "Never set this property or {} on a deployment target",
                ApplicationConfiguration.TRAINING_ENABLED, ApplicationConfiguration.TRAINING_MODE,
                exit ? ", then exits with status 0" : "", Micronaut.TRAINING_ENABLED_ENVIRONMENT_VARIABLE);
            if (environment.containsProperties(WARMUP_PREFIX)) {
                LOG.warn("Training run ({}=load): the {} settings are ignored, because this mode starts no server and sends no warm-up request",
                    ApplicationConfiguration.TRAINING_MODE, WARMUP_PREFIX);
            }
        }
        try (applicationContext) {
            long start = System.nanoTime();
            Result result = load(applicationContext);
            if (LOG.isInfoEnabled()) {
                LOG.info("Training run ({}=load): loaded {} of {} bean definitions and the {} types they name in {}ms, skipped {} that could not be loaded",
                    ApplicationConfiguration.TRAINING_MODE, result.loaded(), result.references(), result.types(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), result.skipped());
            }
            if (LOG.isWarnEnabled()) {
                LOG.warn("Training run ({}=true, {}=load): bean definitions loaded, closing the context{}. This JVM was a training run and served no traffic",
                    ApplicationConfiguration.TRAINING_ENABLED, ApplicationConfiguration.TRAINING_MODE, exit ? " and exiting with status 0" : "");
            }
        }
        if (exit) {
            System.exit(0);
        }
    }

    /**
     * Configures the context without starting it, then loads every enabled bean definition and
     * reads the metadata of it that names classes. A definition that cannot be loaded, for example
     * because a class it names is absent, is skipped and logged.
     *
     * @param applicationContext The application context, built and not started, with a started environment
     * @return What was loaded
     */
    static Result load(ApplicationContext applicationContext) {
        if (!(applicationContext instanceof ConfigurableBeanContext configurable)) {
            throw new IllegalStateException("The " + MODE_LOAD + " training mode needs a " + ConfigurableBeanContext.class.getName()
                + ", but the application context is a " + applicationContext.getClass().getName());
        }
        registerEnvironment(applicationContext);
        configurable.configure();

        Collection<BeanDefinitionReference<Object>> references = applicationContext.getBeanDefinitionReferences();
        Set<Class<?>> types = Collections.newSetFromMap(new IdentityHashMap<>());
        int loaded = 0;
        int skipped = 0;
        for (BeanDefinitionReference<Object> reference : references) {
            try {
                BeanDefinition<Object> definition = loadIfEnabled(applicationContext, reference);
                if (definition != null) {
                    collectTypes(definition, types);
                    loaded++;
                }
            } catch (RuntimeException | LinkageError e) {
                // As in a normal start, where such a definition only fails once something asks for the bean
                skipped++;
                if (LOG.isInfoEnabled()) {
                    LOG.info("Training run ({}=load): skipped bean definition {}: {}", ApplicationConfiguration.TRAINING_MODE, reference.getBeanDefinitionName(), e.toString());
                }
            }
        }
        return new Result(references.size(), loaded, skipped, types.size());
    }

    /**
     * Registers the two definitions that {@code DefaultApplicationContext} registers when it
     * starts, before it reads the bean definitions: without them a condition such as
     * {@code @Requires(beans = Environment.class)} would not match in a context that is only
     * configured. Registering a definition creates no bean: both objects already exist.
     *
     * @param applicationContext The application context
     */
    private static void registerEnvironment(ApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();
        applicationContext.registerBeanDefinition(RuntimeBeanDefinition.builder(environment)
            .singleton(true)
            .exposedTypes(Environment.class, PropertyResolver.class, ResourceLoader.class)
            .qualifier(PrimaryQualifier.instance())
            .build());
        applicationContext.registerBeanDefinition(RuntimeBeanDefinition.builder(environment.getConversionService())
            .singleton(true)
            .exposedTypes(MutableConversionService.class, ConversionService.class)
            .build());
    }

    /**
     * Evaluates the conditions of a reference in the two steps the container uses: the conditions
     * that need no loaded definition, then, once loaded, the others.
     *
     * @param context The configured context
     * @param reference The reference
     * @return The loaded definition, or {@code null} if it is disabled
     */
    private static @Nullable BeanDefinition<Object> loadIfEnabled(BeanContext context, BeanDefinitionReference<Object> reference) {
        boolean referenceEnabled = reference instanceof AbstractInitializableBeanDefinitionAndReference<Object> definitionAndReference
            ? definitionAndReference.isEnabled(context, null, true)
            : reference.isEnabled(context);
        if (!referenceEnabled) {
            return null;
        }
        BeanDefinition<Object> definition = reference.load(context);
        boolean definitionEnabled = definition instanceof AbstractInitializableBeanDefinitionAndReference<Object> definitionAndReference
            ? definitionAndReference.isEnabled(context, null, false)
            : definition.isEnabled(context);
        return definitionEnabled ? definition : null;
    }

    /**
     * Reads the metadata of a definition that names classes. A compiled definition holds them as
     * class literals, most of which its static initializer has resolved by the time the definition
     * is loaded: reading them also covers a definition that builds its metadata lazily. None of
     * these accessors initializes a class.
     *
     * @param definition The definition
     * @param types The types collected so far
     */
    private static void collectTypes(BeanDefinition<Object> definition, Set<Class<?>> types) {
        types.add(definition.getBeanType());
        types.addAll(definition.getExposedTypes());
        Class<?> declaringType = definition.getDeclaringType().orElse(null);
        if (declaringType != null) {
            types.add(declaringType);
        }
        if (definition instanceof AdvisedBeanType<?> advised) {
            types.add(advised.getInterceptedType());
        }
        if (definition instanceof ProxyBeanDefinition<Object> proxy) {
            types.add(proxy.getTargetType());
            types.add(proxy.getTargetDefinitionType());
        }
        collectTypes(definition.getConstructor().getArguments(), types);
        // The post-construct and pre-destroy methods are among the injected methods
        for (MethodInjectionPoint<Object, ?> method : definition.getInjectedMethods()) {
            collectTypes(method.getArguments(), types);
        }
        for (FieldInjectionPoint<Object, ?> field : definition.getInjectedFields()) {
            collectTypes(field.asArgument(), types);
        }
        for (ExecutableMethod<Object, ?> method : definition.getExecutableMethods()) {
            types.add(method.getDeclaringType());
            collectTypes(method.getReturnType().asArgument(), types);
            collectTypes(method.getArguments(), types);
        }
    }

    private static void collectTypes(Argument<?>[] arguments, Set<Class<?>> types) {
        for (Argument<?> argument : arguments) {
            collectTypes(argument, types);
        }
    }

    private static void collectTypes(Argument<?> argument, Set<Class<?>> types) {
        types.add(argument.getType());
        collectTypes(argument.getTypeParameters(), types);
    }

    /**
     * What a {@code load} training run did.
     *
     * @param references The bean definition references of the context
     * @param loaded The enabled definitions that were loaded
     * @param skipped The references that could not be evaluated or loaded
     * @param types The distinct types the loaded definitions name
     */
    record Result(int references, int loaded, int skipped, int types) {
    }
}
