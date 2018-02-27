/*
 * Copyright 2018 original authors
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
package org.particleframework.function.client.http;

import org.particleframework.core.async.publisher.Publishers;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.type.Argument;
import org.particleframework.function.client.FunctionDefinition;
import org.particleframework.function.client.FunctionInvoker;
import org.particleframework.function.client.FunctionInvokerChooser;
import org.particleframework.function.client.exceptions.FunctionExecutionException;
import org.particleframework.function.client.exceptions.FunctionNotFoundException;
import org.particleframework.function.executor.FunctionExecutor;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.client.DefaultHttpClient;
import org.particleframework.http.client.HttpClient;
import org.particleframework.http.client.HttpClientConfiguration;
import org.particleframework.http.client.LoadBalancer;
import org.particleframework.http.codec.MediaTypeCodecRegistry;
import org.particleframework.http.filter.HttpClientFilter;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.io.Closeable;
import java.net.URI;
import java.util.Optional;

/**
 * A {@link FunctionExecutor} that uses a {@link HttpClient} to execute a remote function definition
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class HttpFunctionExecutor<I,O> extends DefaultHttpClient implements FunctionInvoker<I,O>, Closeable, FunctionInvokerChooser {

    public HttpFunctionExecutor(HttpClientConfiguration configuration, MediaTypeCodecRegistry codecRegistry, HttpClientFilter... filters) {
        super(LoadBalancer.empty(), configuration, codecRegistry, filters);
    }

    @Override
    public O invoke(FunctionDefinition definition, I input, Argument<O> outputType) {
        Optional<URI> opt = definition.getURI();
        if(!opt.isPresent()) {
            throw new FunctionNotFoundException(definition.getName());
        }
        else {
            URI uri = opt.get();
            HttpRequest request;
            if(input == null) {
                request = HttpRequest.GET(uri.toString());
            }
            else {
                request = HttpRequest.POST(uri.toString(), input);
            }
            if(Publishers.isPublisher(outputType.getType())) {
                Publisher publisher = retrieve(request, outputType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT));
                return ConversionService.SHARED.convert(publisher, outputType).orElseThrow(()->
                        new FunctionExecutionException("Unsupported Reactive type: " + outputType.getType())
                );
            }
            else {
                return (O)toBlocking().retrieve(request, outputType);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I1, O2> Optional<FunctionInvoker<I1, O2>> choose(FunctionDefinition definition) {
        if(definition.getURI().isPresent()) {
            return Optional.of( (FunctionInvoker)this);
        }

        return Optional.empty();
    }
}
