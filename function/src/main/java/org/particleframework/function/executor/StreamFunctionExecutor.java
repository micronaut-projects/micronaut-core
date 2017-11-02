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
import org.particleframework.context.env.Environment;
import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionError;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.exceptions.ConversionErrorException;
import org.particleframework.core.io.Writable;
import org.particleframework.core.reflect.ClassUtils;
import org.particleframework.core.reflect.exception.InvocationException;
import org.particleframework.core.type.Argument;
import org.particleframework.core.type.ReturnType;
import org.particleframework.function.FunctionRegistry;
import org.particleframework.http.MediaType;
import org.particleframework.http.codec.CodecException;
import org.particleframework.http.codec.MediaTypeCodec;
import org.particleframework.http.codec.MediaTypeCodecRegistry;
import org.particleframework.inject.ExecutableMethod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * <p>A base function executor for handling input and output as streams</p>
 *
 * @param <C> the context type
 * @author Graeme Rocher
 * @since 1.0
 */
public class StreamFunctionExecutor<C> extends AbstractExecutor<C> {
    /**
     * Execute the function for the given input and output
     * @param input The input
     * @param output The output
     * @throws IOException If an I/O exception occurs
     */
    public void execute(InputStream input, OutputStream output) throws IOException {
        execute(input, output, null);
    }

    /**
     * Execute the function with given context object
     *
     * @param input The {@link InputStream}
     * @param output THe {@link OutputStream}
     * @param context The context object
     * @throws IOException If an error occurs
     */
    protected void execute(InputStream input, OutputStream output, C context) throws IOException {
        ApplicationContext applicationContext = buildApplicationContext(context);
        if(context == null) {
            context = (C) applicationContext;
        }
        Environment env = startEnvironment(applicationContext);
        String functionName = resolveFunctionName(env);

        FunctionRegistry functionRegistry = applicationContext.getBean(FunctionRegistry.class);
        ExecutableMethod<Object, Object> method = resolveFunction(functionRegistry, functionName);

        Argument[] requiredArguments = method.getArguments();
        int argCount = requiredArguments.length;
        Object result;
        Object bean = applicationContext.getBean(method.getDeclaringType());

        switch (argCount) {
            case 0:
                result = method.invoke(bean);
                break;
            case 1:

                Argument arg = requiredArguments[0];
                Object value = decodeInputArgument(env, functionRegistry, arg, input);
                result = method.invoke(bean, value);
                break;
            case 2:
                Argument firstArgument = requiredArguments[0];
                Argument secondArgument = requiredArguments[1];
                Object first = decodeInputArgument(env, functionRegistry, firstArgument, input);
                Object second = decodeContext(env, secondArgument, context);
                result = method.invoke(bean, first, second);
                break;
            default:
                throw new InvocationException("Function ["+functionName+"] cannot be made executable.");
        }
        if(result != null) {
            encode(env, functionRegistry, method.getReturnType().getType(), result, output);
        }
    }


    static void encode(Environment environment, FunctionRegistry registry, Class returnType, Object result, OutputStream output) throws IOException {
        if(ClassUtils.isJavaLangType(returnType)) {
            if(result instanceof Byte) {
                output.write((Byte) result);
            }
            else if(result instanceof Boolean) {
                output.write(((Boolean) result) ? 1 : 0);
            }
            else if(result instanceof byte[]) {
                output.write((byte[]) result);
            }
            else {
                byte[] bytes = environment
                        .convert(result, byte[].class)
                        .orElseThrow(() -> new InvocationException("Unable to convert result [" + result + "] for output stream"));
                output.write(bytes);
            }
        }
        else {
            if(result instanceof Writable) {
                Writable writable = (Writable) result;
                writable.writeTo(output, environment.getProperty(FunctionRegistry.FUNCTION_CHARSET, Charset.class, StandardCharsets.UTF_8));
            }
            else {
                Optional<MediaTypeCodec> codec = registry instanceof MediaTypeCodecRegistry ? ((MediaTypeCodecRegistry) registry).findCodec(MediaType.APPLICATION_JSON_TYPE) : Optional.empty();


                if(codec.isPresent()) {
                    codec.get().encode(result, output);
                }
                else {
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
            FunctionRegistry functionRegistry,
            Argument<?> arg,
            InputStream input) {
        Class<?> argType = arg.getType();
        if(ClassUtils.isJavaLangType(argType)) {
            Object converted = doConvertInput(conversionService, arg, input);
            if (converted != null) return converted;
        }
        else if(argType.isInstance(input)) {
            return input;
        }
        else {

            if(functionRegistry instanceof MediaTypeCodecRegistry) {
                Optional<MediaTypeCodec> registeredDecoder = ((MediaTypeCodecRegistry) functionRegistry).findCodec(MediaType.APPLICATION_JSON_TYPE);
                if(registeredDecoder.isPresent()) {
                    MediaTypeCodec decoder = registeredDecoder.get();
                    return decoder.decode(argType, input);
                }
            }
        }
        throw new CodecException("Unable to decode argument from stream: " + arg);
    }

    private Object decodeContext(
            ConversionService<?> conversionService,
            Argument<?> arg,
            Object context) {
        if(ClassUtils.isJavaLangType(arg.getType())) {
            Object convert = doConvertInput(conversionService, arg, context);
            if (convert != null) return convert;
        }
        throw new CodecException("Unable to decode argument from stream: " + arg);
    }

    private Object doConvertInput(ConversionService<?> conversionService, Argument<?> arg, Object object) {
        ArgumentConversionContext conversionContext = ConversionContext.of(arg);
        Optional<?> convert = conversionService.convert(object, conversionContext);
        if(convert.isPresent()) {
            return convert.get();
        }
        else {
            Optional<ConversionError> lastError = conversionContext.getLastError();
            if(lastError.isPresent()) {
                throw new ConversionErrorException(arg, lastError.get());
            }
        }
        return null;
    }

}
