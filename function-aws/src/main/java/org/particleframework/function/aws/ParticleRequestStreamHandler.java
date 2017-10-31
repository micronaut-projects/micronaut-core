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
import org.particleframework.core.convert.value.ConvertibleValues;
import org.particleframework.core.io.Writable;
import org.particleframework.core.reflect.ClassUtils;
import org.particleframework.core.reflect.exception.InvocationException;
import org.particleframework.core.type.Argument;
import org.particleframework.core.util.StringUtils;
import org.particleframework.function.FunctionRegistry;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.MediaType;
import org.particleframework.http.decoder.MediaTypeDecoder;
import org.particleframework.http.decoder.MediaTypeDecoderRegistry;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.web.router.Router;
import org.particleframework.web.router.UriRouteMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOG = LoggerFactory.getLogger(ParticleRequestStreamHandler.class);

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        ApplicationContext applicationContext = buildApplicationContext();
        registerContextBeans(context, applicationContext);
        applicationContext.start();

        Environment environment = applicationContext.getEnvironment();

        Router router = applicationContext.getBean(Router.class);
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper.class);
        String functionName = resolveFunctionName(applicationContext);
        if(functionName == null) {
            throw new IllegalStateException("No function functions found");
        }

        Optional<UriRouteMatch<Object>> potentialRoute = router.find(HttpMethod.POST, "/" + functionName).findFirst();
        if(!potentialRoute.isPresent()) {
            potentialRoute = router.find(HttpMethod.GET, "/" + functionName).findFirst();
        }
        UriRouteMatch<Object> match = potentialRoute.orElseThrow(() -> new IllegalStateException("No function found for name: " + functionName));

        List<Argument> requiredArguments = match.getRequiredArguments();
        int argCount = requiredArguments.size();
        Object result;
        switch (argCount) {
            case 0:
                result = match.execute();
            break;
            case 1:
                Argument arg = requiredArguments.get(0);
                match = decodeArgument(applicationContext, match, arg, input);
                if(match.isExecutable()) {
                    result = match.execute();
                }
                else {
                    throw new InvocationException("Function ["+functionName+"] cannot be made executable");
                }
            break;
            case 2:
                Argument firstArgument = requiredArguments.get(0);
                Argument secondArgument = requiredArguments.get(1);
                match = match.fulfill(Collections.singletonMap(
                        secondArgument.getName(),
                        context
                ));
                match = decodeArgument(applicationContext, match, firstArgument, input);
                if(match.isExecutable()) {
                    result = match.execute();
                }
                else {
                    throw new InvocationException("Function ["+functionName+"] cannot be made executable");
                }
            break;
            default:
                throw new InvocationException("Function ["+functionName+"] cannot be made executable.");
        }
        if(result != null) {
            encode(environment, objectMapper, result, output);
        }

    }

    /**
     * @return Build the {@link ApplicationContext} to use
     */
    protected ApplicationContext buildApplicationContext() {
        return ApplicationContext.build(Environment.DEVELOPMENT);
    }

    /**
     * Resolves the function name to execute
     * @param environment The environment
     * @return The function name or null if it cannot be determined
     */
    protected String resolveFunctionName(ApplicationContext environment) {
        String name = environment.getProperty(FunctionRegistry.FUNCTION_NAME, String.class, (String) null);
        if(name == null) {
            Optional<? extends ExecutableMethod<?, ?>> method = environment.getBean(FunctionRegistry.class).findFirst();
            if(method.isPresent()) {
                ExecutableMethod<?, ?> m = method.get();
                name = m.findAnnotation(org.particleframework.function.Function.class)
                        .map(org.particleframework.function.Function::value)
                        .orElse(m.getMethodName());
                if(StringUtils.isEmpty(name)) {
                    name = m.getMethodName();
                }
            }
            else if(LOG.isDebugEnabled()) {
                LOG.debug("No function definitions found");
            }
        }
        if(LOG.isDebugEnabled() && name != null) {
            LOG.debug("Resolved function name: {}", name);
        }
        return name;
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

    private UriRouteMatch<Object> decodeArgument(ApplicationContext applicationContext, UriRouteMatch<Object> match, Argument arg, InputStream input) {
        if(ClassUtils.isJavaLangType(arg.getType())) {
            match = match.fulfill(
                    Collections.singletonMap(
                            arg.getName(),
                            input
                    )
            );
        } else {
            Optional<MediaTypeDecoder> registered = applicationContext.getBean(MediaTypeDecoderRegistry.class)
                    .findDecoder(MediaType.APPLICATION_JSON_TYPE);
            if(registered.isPresent()) {
                MediaTypeDecoder decoder = registered.get();
                Object decoded = decoder.decode(arg.getType(), input);
                match = match.fulfill(
                        Collections.singletonMap(
                                arg.getName(),
                                decoded
                        )
                );

                if(!match.isExecutable()) {
                    Optional<ConvertibleValues> convertedValues = applicationContext.getEnvironment().convert(decoded, ConvertibleValues.class);
                    if(convertedValues.isPresent()) {
                        ConvertibleValues values = convertedValues.get();
                        Optional converted = values.get(arg.getName(), arg);
                        if(converted.isPresent()) {
                            match = match.fulfill(
                                    Collections.singletonMap(
                                            arg.getName(),
                                            converted.get()
                                    )
                            );
                        }
                    }
                }
            }
        }
        return match;
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
