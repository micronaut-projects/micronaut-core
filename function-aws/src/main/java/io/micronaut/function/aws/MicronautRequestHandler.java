/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.function.aws;

import com.amazonaws.services.lambda.runtime.*;
import io.micronaut.context.ApplicationContext;
import io.micronaut.function.executor.AbstractFunctionExecutor;

/**
 * <p>An Amazon Lambda {@link RequestHandler} implementation for Micronaut {@link io.micronaut.function.FunctionBean}</p>.
 *
 * @param <I>      The request body type
 * @param <O>      The response body type
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class MicronautRequestHandler<I, O> extends AbstractFunctionExecutor<I, O, Context> implements RequestHandler<I, O> {

    @Override
    public final O handleRequest(I input, Context context) {
        ApplicationContext applicationContext = buildApplicationContext(context);
        startEnvironment(applicationContext);
        return applicationContext.inject(this).execute(input);
    }

    @Override
    protected ApplicationContext buildApplicationContext(Context context) {
        ApplicationContext applicationContext = super.buildApplicationContext(context);
        if (context != null) {
            registerContextBeans(context, applicationContext);
        }
        return applicationContext;
    }

    /**
     * Register the beans in the application.
     *
     * @param context context
     * @param applicationContext application context
     */
    static void registerContextBeans(Context context, ApplicationContext applicationContext) {
        applicationContext.registerSingleton(context);
        LambdaLogger logger = context.getLogger();
        if (logger != null) {
            applicationContext.registerSingleton(logger);
        }
        ClientContext clientContext = context.getClientContext();
        if (clientContext != null) {
            applicationContext.registerSingleton(clientContext);
        }
        CognitoIdentity identity = context.getIdentity();
        if (identity != null) {
            applicationContext.registerSingleton(identity);
        }
    }
}
