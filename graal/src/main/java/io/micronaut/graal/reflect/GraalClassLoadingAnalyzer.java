/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.graal.reflect;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanDefinitionRegistry;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.reflect.ClassLoadingReporter;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.HttpMethodMapping;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.runtime.server.EmbeddedServer;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A main class that can be used to analyze the classloading requirements of a Micronaut application.
 *
 * @author graemerocher
 * @since 1.0
 */

@Requires(classes = Client.class)
@Requires(property = GraalClassLoadingReporter.GRAAL_CLASS_ANALYSIS)
@Singleton
public final class GraalClassLoadingAnalyzer {

    private final BeanDefinitionRegistry registry;

    /**
     * Default constructor.
     * @param registry The bean registry
     */
    GraalClassLoadingAnalyzer(BeanDefinitionRegistry registry) {
        this.registry = registry;
    }

    /**
     * Main method.
     * @param args The arguments
     */
    public static void main(String... args) {
        // enable Graal class analysis
        System.setProperty(GraalClassLoadingReporter.GRAAL_CLASS_ANALYSIS, Boolean.TRUE.toString());

        if (ArrayUtils.isNotEmpty(args)) {
            System.setProperty(GraalClassLoadingReporter.REFLECTION_JSON_FILE, args[0]);
        }

        try {
            ApplicationContext applicationContext = ApplicationContext.run();
            // following beans may impact classloading, so load them.
            applicationContext.findBean(EmbeddedServer.class);
            applicationContext.getBeansOfType(ExecutableMethodProcessor.class);
            // finish up
            applicationContext.stop();
        } catch (Throwable e) {
            System.err.println("An error occurred analyzing class requirements: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }

    /**
     * Processes the startup event and reads common ingress/outgress points to ensure Jackson can
     * marshall the method arguments.
     *
     * @param startupEvent The startup event
     */
    @EventListener
    public void onStartup(StartupEvent startupEvent) {
        processHttpMethods(
                registry.getBeanDefinitions(Qualifiers.byStereotype(Client.class))
        );
        processHttpMethods(
                registry.getBeanDefinitions(Qualifiers.byStereotype(Controller.class))
        );
    }

    private void processHttpMethods(Collection<BeanDefinition<?>> clientBeans) {
        for (BeanDefinition<?> clientBean : clientBeans) {
            final Optional<Class[]> additionalTypes = clientBean.getValue(TypeHint.class, Class[].class);
            additionalTypes.ifPresent(classes -> {
                for (Class aClass : classes) {
                    ClassLoadingReporter.reportBeanPresent(aClass);
                }
            });
            final Collection<? extends ExecutableMethod<?, ?>> executableMethods = clientBean.getExecutableMethods();
            executableMethods.parallelStream().forEach((Consumer<ExecutableMethod<?, ?>>) executableMethod -> {
                if (executableMethod.hasStereotype(HttpMethodMapping.class)) {
                    final ReturnType<?> returnType = executableMethod.getReturnType();
                    final Class<?> javaType = returnType.getType();
                    if (!ClassUtils.isJavaLangType(javaType)) {
                        ClassLoadingReporter.reportBeanPresent(javaType);
                    }
                    reportArguments(returnType.getTypeParameters());
                    reportArguments(executableMethod.getArguments());
                }
            });
        }
    }

    private void reportArguments(Argument... arguments) {
        if (ArrayUtils.isNotEmpty(arguments)) {
            for (Argument argument : arguments) {
                final Class argType = argument.getType();
                if (!ClassUtils.isJavaLangType(argType)) {
                    ClassLoadingReporter.reportBeanPresent(argType);
                }
                reportArguments(argument.getTypeParameters());
            }
        }
    }
}
