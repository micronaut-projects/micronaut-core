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
import org.particleframework.core.async.subscriber.CompletionAwareSubscriber;
import org.particleframework.core.beans.BeanMap;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.core.type.Argument;
import org.particleframework.core.type.MutableArgumentValue;
import org.particleframework.core.type.ReturnType;
import org.particleframework.core.util.ArrayUtils;
import org.particleframework.core.util.StringUtils;
import org.particleframework.http.*;
import org.particleframework.http.annotation.Body;
import org.particleframework.http.annotation.Header;
import org.particleframework.http.annotation.HttpMethodMapping;
import org.particleframework.http.client.BlockingHttpClient;
import org.particleframework.http.client.Client;
import org.particleframework.http.client.ClientPublisherResultTransformer;
import org.particleframework.http.client.HttpClient;
import org.particleframework.http.client.exceptions.HttpClientException;
import org.particleframework.http.client.exceptions.HttpClientResponseException;
import org.particleframework.http.uri.UriMatchTemplate;
import org.particleframework.http.uri.UriTemplate;
import org.particleframework.runtime.server.EmbeddedServer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Introduction advice that implements the {@link Client} annotation
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class HttpClientIntroductionAdvice implements MethodInterceptor<Object, Object>, Closeable, AutoCloseable {

    final BeanContext beanContext;
    private final Optional<EmbeddedServer> embeddedServer;
    private final Map<Integer, ClientRegistration> clients = new ConcurrentHashMap<>();
    private final ClientPublisherResultTransformer[] transformers;

    public HttpClientIntroductionAdvice(BeanContext beanContext, Optional<EmbeddedServer> embeddedServer, ClientPublisherResultTransformer...transformers) {
        this.beanContext = beanContext;
        this.embeddedServer = embeddedServer;
        this.transformers = transformers != null ? transformers : new ClientPublisherResultTransformer[0];
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Client clientAnnotation = context.getAnnotation(Client.class);
        if(clientAnnotation == null) {
            throw new IllegalStateException("Client advice called from type that is not annotated with @Client: " + context);
        }

        String[] clientId = clientAnnotation.value();

        ClientRegistration reg = getClient(clientId);
        Optional<Class<? extends Annotation>> httpMethodMapping = context.getAnnotationTypeByStereotype(HttpMethodMapping.class);
        if(httpMethodMapping.isPresent()) {
            String uri = context.getValue(HttpMethodMapping.class, String.class).orElse( "");
            Class<? extends Annotation> annotationType = httpMethodMapping.get();

            HttpMethod httpMethod = HttpMethod.valueOf(annotationType.getSimpleName().toUpperCase());

            ReturnType returnType = context.getReturnType();
            Class<?> javaReturnType = returnType.getType();


            String contextPath = reg.contextPath;
            UriMatchTemplate uriTemplate = UriMatchTemplate.of(contextPath != null ? contextPath : "/");
            if(!(uri.length() == 1 && uri.charAt(0) == '/')) {
                uriTemplate = uriTemplate.nest(uri);
            }

            Map<String, Object> paramMap = context.getParameterValueMap();
            List<String> uriVariables = uriTemplate.getVariables();

            boolean variableSatisfied = uriVariables.isEmpty() || uriVariables.containsAll(paramMap.keySet());
            MutableHttpRequest<Object> request;
            Object body = null;
            Map<String, MutableArgumentValue<?>> parameters = context.getParameters();
            Argument[] arguments = context.getArguments();
            Map<String,String> headers = new LinkedHashMap<>(3);
            List<Argument> bodyArguments = new ArrayList<>();
            for (Argument argument : arguments) {
                String argumentName = argument.getName();
                if(argument.isAnnotationPresent(Body.class)) {
                    body = parameters.get(argumentName).getValue();
                    break;
                }
                else if(argument.isAnnotationPresent(Header.class)) {

                    String headerName = argument.getAnnotation(Header.class).value();
                    if(StringUtils.isEmpty(headerName)) {
                        headerName = NameUtils.hyphenate(argumentName);
                    }
                    MutableArgumentValue<?> value = parameters.get(argumentName);
                    String finalHeaderName = headerName;
                    ConversionService.SHARED.convert(value.getValue(), String.class)
                            .ifPresent(o -> headers.put(finalHeaderName, o));
                }
                else if(!uriVariables.contains(argumentName)){
                    bodyArguments.add(argument);
                }
            }
            if(HttpMethod.permitsRequestBody(httpMethod)) {

                if(body == null && !bodyArguments.isEmpty()) {
                    Map<String,Object> bodyMap = new LinkedHashMap<>();

                    for (Argument bodyArgument : bodyArguments) {
                        String argumentName = bodyArgument.getName();
                        MutableArgumentValue<?> value = parameters.get(argumentName);
                        bodyMap.put(argumentName, value.getValue());
                    }

                    body = bodyMap;
                }

                if(body != null) {
                    if(!variableSatisfied) {

                        if(body instanceof Map) {
                            paramMap.putAll((Map)body);
                            uri = uriTemplate.expand(paramMap);
                            request = HttpRequest.create(httpMethod, uri);
                        }
                        else{
                            paramMap.putAll(BeanMap.of(body));
                            uri = uriTemplate.expand(paramMap);
                            request = HttpRequest.create(httpMethod, uri);
                        }
                    }
                    else {
                        uri = uriTemplate.expand(paramMap);
                        request = HttpRequest.create(httpMethod, uri);
                    }
                    request.body(body);
                }
                else {
                    uri = uriTemplate.expand(paramMap);
                    request = HttpRequest.create(httpMethod, uri);
                }
            }
            else {
                uri = uriTemplate.expand(paramMap);
                request = HttpRequest.create(httpMethod, uri);
            }

            if(!headers.isEmpty()) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    request.header(entry.getKey(), entry.getValue());
                }
            }
            HttpClient httpClient = reg.httpClient;

            boolean isFuture = CompletableFuture.class.isAssignableFrom(javaReturnType);
            if(Publishers.isPublisher(javaReturnType) || isFuture) {
                Argument<?> publisherArgument = returnType.asArgument().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                Class<?> argumentType = publisherArgument.getType();
                Publisher<?> publisher;
                if(HttpResponse.class.isAssignableFrom(argumentType)) {
                    publisher = httpClient.exchange(
                            request, returnType.asArgument().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT)
                    );
                }
                else {
                    publisher = httpClient.retrieve(
                            request, publisherArgument
                    );
                }

                if(isFuture) {
                    CompletableFuture<Object> future = new CompletableFuture<>();
                    publisher.subscribe(new CompletionAwareSubscriber<Object>() {
                        AtomicReference<Object> reference = new AtomicReference<>();
                        @Override
                        protected void doOnSubscribe(Subscription subscription) {
                            subscription.request(1);
                        }

                        @Override
                        protected void doOnNext(Object message) {
                            reference.set(message);
                        }

                        @Override
                        protected void doOnError(Throwable t) {
                            if(t instanceof HttpClientResponseException) {
                                HttpClientResponseException e = (HttpClientResponseException) t;
                                if( e.getStatus() == HttpStatus.NOT_FOUND) {
                                    future.complete(null);
                                }
                                else {
                                    future.completeExceptionally(t);
                                }
                            }
                            future.completeExceptionally(t);
                        }

                        @Override
                        protected void doOnComplete() {
                            future.complete(reference.get());
                        }
                    });
                    return future;
                }
                else {
                    Object finalPublisher = ConversionService.SHARED.convert(publisher, javaReturnType).orElseThrow(() ->
                            new HttpClientException("Unconvertible Reactive Streams Publisher Type: " + javaReturnType)
                    );
                    for (ClientPublisherResultTransformer transformer : transformers) {
                        finalPublisher = transformer.transform(finalPublisher);
                    }
                    return finalPublisher;
                }
            }

            else {
                BlockingHttpClient blockingHttpClient = httpClient.toBlocking();
                if(HttpResponse.class.isAssignableFrom(javaReturnType)) {
                    return blockingHttpClient.exchange(
                            request, returnType.asArgument().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT)
                    );
                }
                else if(void.class == javaReturnType) {
                    blockingHttpClient.exchange(request);
                    return null;
                }
                else {
                    try {
                        return blockingHttpClient.retrieve(
                                request, returnType.asArgument()
                        );
                    } catch (HttpClientResponseException e) {
                        if( e.getStatus() == HttpStatus.NOT_FOUND) {
                            if(javaReturnType == Optional.class) {
                                return Optional.empty();
                            }
                            return null;
                        }
                        throw e;
                    }
                }
            }
        }
        throw new UnsupportedOperationException("Cannot implement method that is not annotated with an HTTP method type");
    }

    /**
     * Hook to allow dealing with the final converted Publisher type
     *
     * @param finalPublisher The final publisher
     * @return The resulting publisher. Never null
     */
    protected Object finalizePublisher(Object finalPublisher) {
        return finalPublisher;
    }

    private ClientRegistration getClient(String[] clientId) {
        return clients.computeIfAbsent(Arrays.hashCode(clientId), integer -> {
            if(ArrayUtils.isEmpty(clientId) || StringUtils.isEmpty(clientId[0])) {
                throw new HttpClientException("No value specified for @Client");
            }
            String reference = clientId[0];
            URL url;
            String contextPath = "";
            if(reference.startsWith("/")) {
                // current server reference
                if(embeddedServer.isPresent()) {

                    url = embeddedServer.get().getURL();
                    if(reference.length() > 1) {
                        contextPath = reference;
                    }
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
            HttpClient client = beanContext.createBean(HttpClient.class, Collections.singletonMap("url", url));
            return new ClientRegistration(client, contextPath);
        });
    }


    @Override
    @PreDestroy
    public void close() throws IOException {
        for (ClientRegistration registration : clients.values()) {
            HttpClient httpClient = registration.httpClient;
            httpClient.close();
        }
    }

    class ClientRegistration {
        final HttpClient httpClient;
        final String contextPath;

        public ClientRegistration(HttpClient httpClient, String contextPath) {
            this.httpClient = httpClient;
            this.contextPath = contextPath;
        }
    }
}
