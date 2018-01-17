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
package org.particleframework.http.client.interceptor;

import org.particleframework.aop.MethodInterceptor;
import org.particleframework.aop.MethodInvocationContext;
import org.particleframework.context.BeanContext;
import org.particleframework.context.annotation.Prototype;
import org.particleframework.context.exceptions.ConfigurationException;
import org.particleframework.context.exceptions.DependencyInjectionException;
import org.particleframework.core.async.publisher.Publishers;
import org.particleframework.core.type.ReturnType;
import org.particleframework.core.util.ArrayUtils;
import org.particleframework.core.util.StringUtils;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.annotation.HttpMethodMapping;
import org.particleframework.http.client.BlockingHttpClient;
import org.particleframework.http.client.Client;
import org.particleframework.http.client.HttpClient;
import org.particleframework.http.client.exceptions.HttpClientException;
import org.particleframework.runtime.server.EmbeddedServer;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * TODO: Javadoc description
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class HttpClientIntroductionAdvice implements MethodInterceptor, Closeable, AutoCloseable {


    final BeanContext beanContext;
    private final Optional<EmbeddedServer> embeddedServer;
    private final Map<Integer, HttpClient> clients = new ConcurrentHashMap<>();

    public HttpClientIntroductionAdvice(BeanContext beanContext, Optional<EmbeddedServer> embeddedServer) {
        this.beanContext = beanContext;
        this.embeddedServer = embeddedServer;
    }

    @Override
    public Object intercept(MethodInvocationContext context) {
        Client clientAnnotation = context.getAnnotation(Client.class);
        if(clientAnnotation == null) {
            throw new IllegalStateException("Client advice called from type that is not annotated with @Client: " + context);
        }

        String[] clientId = clientAnnotation.value();

        HttpClient httpClient = getClient(clientId);
        Optional<Class<? extends Annotation>> httpMethodMapping = context.getAnnotationTypeByStereotype(HttpMethodMapping.class);
        if(httpMethodMapping.isPresent()) {
            String uri = context.getValue(HttpMethodMapping.class, String.class).orElseThrow(() -> new HttpClientException("No URI specified"));
            if(StringUtils.isEmpty(uri)) {
                throw new HttpClientException("No URI specified");
            }
            Class<? extends Annotation> annotationType = httpMethodMapping.get();

            HttpMethod httpMethod = HttpMethod.valueOf(annotationType.getSimpleName().toUpperCase());

            ReturnType returnType = context.getReturnType();
            Class javaReturnType = returnType.getType();
            if(Publishers.isPublisher(javaReturnType)) {
                
            }
            else {
                BlockingHttpClient blockingHttpClient = httpClient.toBlocking();
                if(HttpResponse.class.isAssignableFrom(javaReturnType)) {
                    return blockingHttpClient.exchange(
                            HttpRequest.create(httpMethod, uri ), returnType.asArgument()
                    );
                }
                else {
                    return blockingHttpClient.retrieve(
                            HttpRequest.create(httpMethod, uri ), returnType.asArgument()
                    );
                }
            }

        }
        throw new UnsupportedOperationException("Cannot implement method that is not annotated with an HTTP method type");
    }

    private HttpClient getClient(String[] clientId) {
        return clients.computeIfAbsent(Arrays.hashCode(clientId), integer -> {
            URL url = resolveClientURL(clientId);
            return beanContext.createBean(HttpClient.class, Collections.singletonMap("url", url));
        });
    }

    private URL resolveClientURL(String[] clientId) {
        if(ArrayUtils.isEmpty(clientId) || StringUtils.isEmpty(clientId[0])) {
            throw new HttpClientException("No value specified for @Client");
        }
        String reference = clientId[0];
        URL url;
        if(reference.length() == 1 && reference.charAt(0) == '/') {
            // current server reference
            if(embeddedServer.isPresent()) {
                url = embeddedServer.get().getURL();
            }
            else {
                throw new HttpClientException("Reference to current server used with @Client when no current server running");
            }
        }
        else if(reference.indexOf('/') > -1) {
            try {
                url = new URL(reference);
            } catch (MalformedURLException e) {
                throw new HttpClientException("Invalid URL ["+reference+"] specified to @Client");
            }
        }
        else {
            throw new HttpClientException( "Unsupported No value specified for @Client");
        }
        return url;
    }

    @Override
    @PreDestroy
    public void close() throws IOException {

    }
}
