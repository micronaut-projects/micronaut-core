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

import static io.micronaut.function.aws.MicronautRequestHandler.registerContextBeans;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import io.micronaut.context.ApplicationContext;
import io.micronaut.function.executor.StreamFunctionExecutor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * <p>An implementation of the {@link RequestStreamHandler} for Micronaut</p>.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class MicronautRequestStreamHandler extends StreamFunctionExecutor<Context> implements RequestStreamHandler {
    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        execute(input, output, context);
    }

    @Override
    protected ApplicationContext buildApplicationContext(Context context) {
        ApplicationContext applicationContext = super.buildApplicationContext(context);
        if (context != null) {
            registerContextBeans(context, applicationContext);
        }
        return applicationContext;
    }
}
