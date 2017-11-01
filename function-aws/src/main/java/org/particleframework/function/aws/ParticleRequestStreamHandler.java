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
package org.particleframework.function.aws;

import com.amazonaws.services.lambda.runtime.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.particleframework.context.ApplicationContext;
import org.particleframework.context.env.Environment;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.io.Writable;
import org.particleframework.core.reflect.ClassUtils;
import org.particleframework.core.reflect.exception.InvocationException;
import org.particleframework.core.type.Argument;
import org.particleframework.function.FunctionRegistry;
import org.particleframework.http.MediaType;
import org.particleframework.http.decoder.DecodingException;
import org.particleframework.http.decoder.MediaTypeDecoder;
import org.particleframework.http.decoder.MediaTypeDecoderRegistry;
import org.particleframework.inject.ExecutableMethod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

/**
 * <p>An implementation of the {@link RequestStreamHandler} for Particle</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ParticleRequestStreamHandler implements RequestStreamHandler {
    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        ApplicationContext applicationContext = buildApplicationContext();
        registerContextBeans(context, applicationContext);
        applicationContext.start();

        Environment env = applicationContext.getEnvironment();
        FunctionRegistry functionRegistry = applicationContext.getBean(FunctionRegistry.class);
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper.class);
        String functionName = resolveFunctionName(env);
        Optional<? extends ExecutableMethod<Object, Object>> registeredMethod;
        if(functionName == null) {
            registeredMethod = functionRegistry.findFirst();
        }
        else {
            registeredMethod = functionRegistry.find(functionName);
        }
        ExecutableMethod<Object, Object> method = registeredMethod
                .orElseThrow(() -> new IllegalStateException("No function found for name: " + functionName));

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
                Object value = decodeArgument(env, functionRegistry, arg, input);
                result = method.invoke(bean, value);
            break;
            case 2:
                Argument firstArgument = requiredArguments[0];
                Argument secondArgument = requiredArguments[1];
                Object first = decodeArgument(env, functionRegistry, firstArgument, input);
                Object second = decodeContext(env, secondArgument, context);
                result = method.invoke(bean, first, second);
            break;
            default:
                throw new InvocationException("Function ["+functionName+"] cannot be made executable.");
        }
        if(result != null) {
            encode(env, objectMapper, result, output);
        }

    }

    protected String resolveFunctionName(Environment env) {
        return env.getProperty(FunctionRegistry.FUNCTION_NAME, String.class, (String)null);
    }

    /**
     * @return Build the {@link ApplicationContext} to use
     */
    protected ApplicationContext buildApplicationContext() {
        return ApplicationContext.build(Environment.DEVELOPMENT);
    }


    private void encode(Environment environment, ObjectMapper objectMapper, Object result, OutputStream output) throws IOException {
        Optional<JsonNode> converted = environment.convert(result, JsonNode.class);
        if(converted.isPresent()) {
            objectMapper.writeValue(output, converted.get());
        }
        else {
            Optional<Writable> writable = environment.convert(result, Writable.class);
            if(writable.isPresent()) {
                writable.get().writeTo(output);
            }
            else {
                byte[] bytes = environment
                        .convert(result, byte[].class)
                        .orElseThrow(() -> new InvocationException("Unable to convert result [" + result + "] for output stream"));
                output.write(bytes);
            }
        }
    }

    private Object decodeArgument(
            ConversionService<?> conversionService,
            FunctionRegistry functionRegistry,
            Argument<?> arg,
            InputStream input) {
        if(ClassUtils.isJavaLangType(arg.getType())) {
            Optional<?> convert = conversionService.convert(input, ConversionContext.of(arg));
            if(convert.isPresent()) {
                return convert.get();
            }
        } else {

            if(functionRegistry instanceof MediaTypeDecoderRegistry) {
                Optional<MediaTypeDecoder> registeredDecoder = ((MediaTypeDecoderRegistry) functionRegistry).findDecoder(MediaType.APPLICATION_JSON_TYPE);
                if(registeredDecoder.isPresent()) {
                    MediaTypeDecoder decoder = registeredDecoder.get();
                    return decoder.decode(arg.getType(), input);
                }
            }
        }
        throw new DecodingException("Unable to decode argument from stream: " + arg);
    }

    private Object decodeContext(
            ConversionService<?> conversionService,
            Argument<?> arg,
            Context context) {
        if(ClassUtils.isJavaLangType(arg.getType())) {
            Optional<?> convert = conversionService.convert(context, ConversionContext.of(arg));
            if(convert.isPresent()) {
                return convert.get();
            }
        }
        throw new DecodingException("Unable to decode argument from stream: " + arg);
    }

    private void registerContextBeans(Context context, ApplicationContext applicationContext) {
        applicationContext.registerSingleton(context);
        LambdaLogger logger = context.getLogger();
        if(logger != null) {
            applicationContext.registerSingleton(logger);
        }
        ClientContext clientContext = context.getClientContext();
        if(clientContext != null) {
            applicationContext.registerSingleton(clientContext);
        }
        CognitoIdentity identity = context.getIdentity();
        if(identity != null) {
            applicationContext.registerSingleton(identity);
        }
    }

}
