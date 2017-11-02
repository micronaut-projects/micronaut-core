/*
 * Copyright 2017 original authors
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
package org.particleframework.function.executor;

import org.particleframework.context.ApplicationContext;
import org.particleframework.core.cli.CommandLine;
import org.particleframework.core.reflect.ClassUtils;
import org.particleframework.function.FunctionRegistry;
import org.particleframework.http.MediaType;
import org.particleframework.http.codec.MediaTypeCodecRegistry;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Function;

/**
 * A super class that can be used to initialize a function
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class FunctionInitializer extends AbstractExecutor implements Closeable, AutoCloseable {

    protected final ApplicationContext applicationContext;

    @SuppressWarnings("unchecked")
    public FunctionInitializer() {
        ApplicationContext applicationContext = buildApplicationContext(null);
        this.applicationContext = applicationContext;
        startEnvironment(this.applicationContext);
        injectThis(applicationContext);
    }

    @Override
    public void close() throws IOException {
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    /**
     * This method is designed to be called when using the {@link FunctionInitializer} from a static Application main method
     *
     * @param args     The arguments passed to main
     * @param supplier The function that executes this function
     * @throws IOException If an error occurs
     */
    protected void run(String[] args, Function<ParseContext, ?> supplier) throws IOException {
        ApplicationContext applicationContext = this.applicationContext;
        ParseContext context = new ParseContext(args);
        try {
            Object result = supplier.apply(context);
            if (result != null) {

                FunctionRegistry bean = applicationContext.getBean(FunctionRegistry.class);
                StreamFunctionExecutor.encode(applicationContext.getEnvironment(), bean, result.getClass(), result, System.out);
            }
        } catch (Exception e) {
            FunctionApplication.exitWithError(context.debug, e);
        }
    }

    /**
     * Injects this instance
     * @param applicationContext The {@link ApplicationContext}
     * @return This injected instance
     */
    protected void injectThis(ApplicationContext applicationContext) {
        applicationContext.inject(this);
    }


    /**
     * The parse context supplied from the {@link #run(String[], Function)} method. Consumers can use the {@link #get(Class)} method to obtain the data is the desired type
     */
    protected class ParseContext {
        private final String data;
        private final boolean debug;

        public ParseContext(String[] args) {
            CommandLine commandLine = FunctionApplication.parseCommandLine(args);
            debug = commandLine.hasOption(FunctionApplication.DEBUG_OPTIONS);
            data = commandLine.hasOption(FunctionApplication.DATA_OPTION) ? commandLine.optionValue(FunctionApplication.DATA_OPTION).toString() : null;
        }

        public <T> T get(Class<T> type) {
            if (data == null) {
                FunctionApplication.exitWithNoData();
                return null;
            } else {
                if (ClassUtils.isJavaLangType(type)) {
                    return applicationContext
                            .getConversionService()
                            .convert(data, type).orElseThrow(() -> newIllegalArgument(type, data));
                } else {
                    MediaTypeCodecRegistry codecRegistry = applicationContext.getBean(MediaTypeCodecRegistry.class);
                    return codecRegistry.findCodec(MediaType.APPLICATION_JSON_TYPE)
                            .map(codec -> codec.decode(type, data))
                            .orElseThrow(() -> newIllegalArgument(type, data));
                }
            }
        }

        private <T> IllegalArgumentException newIllegalArgument(Class<T> dataType, String data) {
            return new IllegalArgumentException("Passed data [" + data + "] cannot be converted to type: " + dataType);
        }

    }
}
