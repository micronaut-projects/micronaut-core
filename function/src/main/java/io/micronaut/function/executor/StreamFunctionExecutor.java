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
import io.micronaut.context.Qualifier;
import io.micronaut.context.env.Environment;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.io.Writable;
import io.micronaut.core.reflect.ClassLoadingReporter;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.exception.InvocationException;
import io.micronaut.core.type.Argument;
import io.micronaut.function.LocalFunctionRegistry;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * <p>A base function executor for handling input and output as streams</p>.
 *
 * @param <C> the context type
 * @author Graeme Rocher
 * @since 1.0
 */
public class StreamFunctionExecutor<C> extends AbstractExecutor<C> {
    /**
     * Execute the function for the given input and output.
     *
     * @param input  The input
     * @param output The output
     * @throws IOException If an I/O exception occurs
     */
    public void execute(InputStream input, OutputStream output) throws IOException {
        execute(input, output, null);
    }

    /**
     * Execute the function with given context object.
     *
     * @param input   The {@link InputStream}
     * @param output  THe {@link OutputStream}
     * @param context The context object
     * @throws IOException If an error occurs
     */
    protected void execute(InputStream input, OutputStream output, C context) throws IOException {
        final ApplicationContext applicationContext = buildApplicationContext(context);
        if (context == null) {
            context = (C) applicationContext;
        }

        final Environment env = startEnvironment(applicationContext);
        final String functionName = resolveFunctionName(env);

        if (functionName == null) {
            throw new InvocationException("No Function name configured. Set 'micronaut.function.name' in your Function configuration");
        }

        LocalFunctionRegistry localFunctionRegistry = applicationContext.getBean(LocalFunctionRegistry.class);
        ExecutableMethod<Object, Object> method = resolveFunction(localFunctionRegistry, functionName);
        Class<?> returnJavaType = method.getReturnType().getType();
        if (ClassLoadingReporter.isReportingEnabled()) {
            ClassLoadingReporter.reportBeanPresent(returnJavaType);
        }

        Argument[] requiredArguments = method.getArguments();
        int argCount = requiredArguments.length;
        Object result;
        Qualifier<Object> qualifier = Qualifiers.byName(functionName);
        Class<Object> functionType = method.getDeclaringType();
        BeanDefinition<Object> beanDefinition = applicationContext.getBeanDefinition(functionType, qualifier);
        Object bean = applicationContext.getBean(functionType, qualifier);
        List<Argument<?>> typeArguments = beanDefinition.getTypeArguments();

        try {
            switch (argCount) {
                case 0:
                    result = method.invoke(bean);
                    break;
                case 1:

                    Argument arg = requiredArguments[0];
                    if (!typeArguments.isEmpty()) {
                        arg = Argument.of(typeArguments.get(0).getType(), arg.getName());
                    }
                    Object value = decodeInputArgument(env, localFunctionRegistry, arg, input);
                    result = method.invoke(bean, value);
                    break;
                case 2:
                    Argument firstArgument = requiredArguments[0];
                    Argument secondArgument = requiredArguments[1];

                    if (!typeArguments.isEmpty()) {
                        firstArgument = Argument.of(typeArguments.get(0).getType(), firstArgument.getName());
                    }

                    Object first = decodeInputArgument(env, localFunctionRegistry, firstArgument, input);
                    Object second = decodeContext(env, secondArgument, context);
                    result = method.invoke(bean, first, second);
                    break;
                default:
                    throw new InvocationException("Function [" + functionName + "] cannot be made executable.");
            }
            if (result != null) {
                encode(env, localFunctionRegistry, returnJavaType, result, output);
            }
        } finally {
            closeApplicationContext();
        }
    }

    /**
     * Close the application context.
     */
    protected void closeApplicationContext() {
        try {
            applicationContext.close();
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * Encode and write to output stream.
     *
     * @param environment environment
     * @param registry local function registry
     * @param returnType return type as Class
     * @param result result object
     * @param output outputstream
     * @throws IOException input/output exception
     */
    static void encode(Environment environment, LocalFunctionRegistry registry, Class returnType, Object result, OutputStream output) throws IOException {
        if (ClassUtils.isJavaLangType(returnType)) {
            if (result instanceof Byte) {
                output.write((Byte) result);
            } else if (result instanceof Boolean) {
                output.write(((Boolean) result) ? 1 : 0);
            } else if (result instanceof byte[]) {
                output.write((byte[]) result);
            } else {
                byte[] bytes = environment
                    .convert(result.toString(), byte[].class)
                    .orElseThrow(() -> new InvocationException("Unable to convert result [" + result + "] for output stream"));
                output.write(bytes);
            }
        } else {
            if (result instanceof Writable) {
                Writable writable = (Writable) result;
                writable.writeTo(output, environment.getProperty(LocalFunctionRegistry.FUNCTION_CHARSET, Charset.class, StandardCharsets.UTF_8));
            } else {
                Optional<MediaTypeCodec> codec = registry instanceof MediaTypeCodecRegistry ? ((MediaTypeCodecRegistry) registry).findCodec(MediaType.APPLICATION_JSON_TYPE) : Optional.empty();


                if (codec.isPresent()) {
                    codec.get().encode(result, output);
                } else {
                    byte[] bytes = environment
                        .convert(result, byte[].class)
                        .orElseThrow(() -> new InvocationException("Unable to convert result [" + result + "] for output stream"));
                    output.write(bytes);
                }
            }
        }
    }

    private Object decodeInputArgument(
        ConversionService<?> conversionService,
        LocalFunctionRegistry localFunctionRegistry,
        Argument<?> arg,
        InputStream input) {
        Class<?> argType = arg.getType();
        ClassLoadingReporter.reportBeanPresent(argType);

        if (ClassUtils.isJavaLangType(argType)) {
            Object converted = doConvertInput(conversionService, arg, input);
            if (converted != null) {
                return converted;
            }
        } else if (argType.isInstance(input)) {
            return input;
        } else {

            if (localFunctionRegistry instanceof MediaTypeCodecRegistry) {
                Optional<MediaTypeCodec> registeredDecoder = ((MediaTypeCodecRegistry) localFunctionRegistry).findCodec(MediaType.APPLICATION_JSON_TYPE);
                if (registeredDecoder.isPresent()) {
                    MediaTypeCodec decoder = registeredDecoder.get();
                    return decoder.decode(arg, input);
                }
            }
        }
        throw new CodecException("Unable to decode argument from stream: " + arg);
    }

    private Object decodeContext(
        ConversionService<?> conversionService,
        Argument<?> arg,
        Object context) {
        if (ClassUtils.isJavaLangType(arg.getType())) {
            Object convert = doConvertInput(conversionService, arg, context);
            if (convert != null) {
                return convert;
            }
        }
        throw new CodecException("Unable to decode argument from stream: " + arg);
    }

    private Object doConvertInput(ConversionService<?> conversionService, Argument<?> arg, Object object) {
        ArgumentConversionContext conversionContext = ConversionContext.of(arg);
        Optional<?> convert = conversionService.convert(object, conversionContext);
        if (convert.isPresent()) {
            return convert.get();
        } else {
            Optional<ConversionError> lastError = conversionContext.getLastError();
            if (lastError.isPresent()) {
                throw new ConversionErrorException(arg, lastError.get());
            }
        }
        return null;
    }
}
