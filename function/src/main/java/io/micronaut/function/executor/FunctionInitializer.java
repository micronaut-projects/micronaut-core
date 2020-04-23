/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.function.executor;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.cli.CommandLine;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.function.LocalFunctionRegistry;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.MediaTypeCodecRegistry;

import java.io.IOException;
import java.util.function.Function;

/**
 * A super class that can be used to initialize a function.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class FunctionInitializer extends AbstractExecutor {

    protected final boolean closeContext;
    private FunctionExitHandler functionExitHandler = new DefaultFunctionExitHandler();

    /**
     * Constructor.
     */
    public FunctionInitializer() {
        ApplicationContext applicationContext = buildApplicationContext(null);
        startThis(applicationContext);
        injectThis(applicationContext);
        applicationContext.registerSingleton(this, false);
        this.closeContext = true;
    }

    /**
     * Start a function for an existing {@link ApplicationContext}.
     *
     * @param applicationContext The application context
     */
    protected FunctionInitializer(ApplicationContext applicationContext) {
        this(applicationContext, true);
    }

    /**
     * Start a function for an existing {@link ApplicationContext}.
     *
     * @param applicationContext The application context
     * @param inject inject this into the application flag
     */
    protected FunctionInitializer(ApplicationContext applicationContext, boolean inject) {
        this.applicationContext = applicationContext;
        this.closeContext = false;
        if (inject) {
            injectThis(applicationContext);
        }
    }

    @Override
    @Internal
    public void close() throws IOException {
        if (closeContext && applicationContext != null) {
            applicationContext.close();
        }
    }

    /**
     * This method is designed to be called when using the {@link FunctionInitializer} from a static Application main method.
     *
     * @param args     The arguments passed to main
     * @param supplier The function that executes this function
     * @throws IOException If an error occurs
     */
    public void run(String[] args, Function<ParseContext, ?> supplier) throws IOException {
        ApplicationContext applicationContext = this.applicationContext;
        this.functionExitHandler = applicationContext.findBean(FunctionExitHandler.class).orElse(this.functionExitHandler);
        ParseContext context = new ParseContext(args);
        try {
            Object result = supplier.apply(context);
            if (result != null) {

                LocalFunctionRegistry bean = applicationContext.getBean(LocalFunctionRegistry.class);
                StreamFunctionExecutor.encode(applicationContext.getEnvironment(), bean, result.getClass(), result, System.out);
                functionExitHandler.exitWithSuccess();
            }
        } catch (Exception e) {
            functionExitHandler.exitWithError(e, context.debug);
        }
    }

    /**
     * Start this environment.
     *
     * @param applicationContext The application context
     */
    protected void startThis(ApplicationContext applicationContext) {
        startEnvironment(applicationContext);
    }

    /**
     * Injects this instance.
     *
     * @param applicationContext The {@link ApplicationContext}
     */
    protected void injectThis(ApplicationContext applicationContext) {
        if (applicationContext != null) {
            applicationContext.inject(this);
        }
    }

    /**
     * The parse context supplied from the {@link #run(String[], Function)} method. Consumers can use the {@link #get(Class)} method to obtain the data is the desired type.
     */
    public class ParseContext {
        private final String data;
        private final boolean debug;

        /**
         * Constructor.
         *
         * @param args command line args
         */
        ParseContext(String[] args) {
            CommandLine commandLine = FunctionApplication.parseCommandLine(args);
            debug = commandLine.hasOption(FunctionApplication.DEBUG_OPTIONS);
            data = commandLine.hasOption(FunctionApplication.DATA_OPTION) ? commandLine.optionValue(FunctionApplication.DATA_OPTION).toString() : null;
        }

        /**
         * Get.
         *
         * @param type type
         * @param <T> generic return type
         * @return Type
         */
        public final <T> T get(Class<T> type) {
            if (ClassUtils.isJavaLangType(type)) {
                return applicationContext
                    .getConversionService()
                    .convert(data, type).orElseThrow(() -> newIllegalArgument(type, data));
            } else {
                MediaTypeCodecRegistry codecRegistry = applicationContext.getBean(MediaTypeCodecRegistry.class);
                return codecRegistry.findCodec(MediaType.APPLICATION_JSON_TYPE)
                    .map(codec -> {
                        if (data != null) {
                            return codec.decode(type, data);
                        } else {
                            // try System.in
                            return codec.decode(type, System.in);
                        }
                    })
                    .orElseThrow(() -> newIllegalArgument(type, data));
            }
        }

        private <T> IllegalArgumentException newIllegalArgument(Class<T> dataType, String data) {
            if (data != null) {
                return new IllegalArgumentException("Passed data [" + data + "] cannot be converted to type: " + dataType);
            } else {
                return new IllegalArgumentException("Input data cannot be converted to type: " + dataType);
            }
        }
    }
}
