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
package io.micronaut.http.client.interceptor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.*;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.HttpMethodMapping;
import io.micronaut.http.client.*;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.jackson.ObjectMapperFactory;
import io.micronaut.jackson.annotation.JacksonFeatures;
import io.micronaut.jackson.codec.JsonMediaTypeCodec;
import io.micronaut.runtime.ApplicationConfiguration;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Introduction advice that implements the {@link Client} annotation
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class HttpClientIntroductionAdvice implements MethodInterceptor<Object, Object>, Closeable, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpClient.class);

    public static final MediaType[] DEFAULT_ACCEPT_TYPES = {MediaType.APPLICATION_JSON_TYPE};
    private final BeanContext beanContext;
    private final Map<Integer, ClientRegistration> clients = new ConcurrentHashMap<>();
    private final ReactiveClientResultTransformer[] transformers;
    private final LoadBalancerResolver loadBalancerResolver;


    public HttpClientIntroductionAdvice(
            BeanContext beanContext,
            LoadBalancerResolver loadBalancerResolver,
            ReactiveClientResultTransformer...transformers) {
        this.beanContext = beanContext;
        this.loadBalancerResolver = loadBalancerResolver;
        this.transformers = transformers != null ? transformers : new ReactiveClientResultTransformer[0];
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Client clientAnnotation = context.getAnnotation(Client.class);
        if(clientAnnotation == null) {
            throw new IllegalStateException("Client advice called from type that is not annotated with @Client: " + context);
        }

        for (MutableArgumentValue<?> argumentValue: context.getParameters().values()) {
            if (argumentValue.getValue() == null && !argumentValue.isAnnotationPresent(Nullable.class)) {
                throw new IllegalArgumentException(String.format("Null values are not allowed to be passed to client methods (%s). Add @javax.validation.Nullable if that is the desired behavior", context.getTargetMethod().toString()));

            }
        }

        ClientRegistration reg = getClient(context, clientAnnotation);
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
            List<NettyCookie> cookies = new ArrayList<>();
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
                else if (argument.isAnnotationPresent(CookieValue.class)) {
                    Object cookieValue = parameters.get(argumentName).getValue();
                    String cookieName = argument.getAnnotation(CookieValue.class).value();
                    if(StringUtils.isEmpty(cookieName)) {
                        cookieName = argumentName;
                    }
                    String finalCookieName = cookieName;

                    ConversionService.SHARED.convert(cookieValue, String.class)
                            .ifPresent(o -> cookies.add(new NettyCookie(finalCookieName, o)));

                }
                else if (argument.isAnnotationPresent(Parameter.class)) {

                    String parameterName = argument.getAnnotation(Parameter.class).value();
                    MutableArgumentValue<?> value = parameters.get(argumentName);
                    ConversionService.SHARED.convert(value.getValue(), String.class)
                            .ifPresent(o -> paramMap.put(parameterName, o));
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
                            BeanMap<Object> beanMap = BeanMap.of(body);
                            for (Map.Entry<String, Object> entry : beanMap.entrySet()) {
                                String k = entry.getKey();
                                Object v = entry.getValue();
                                if(v != null) {
                                    paramMap.put( k, v );
                                }
                            }
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

            cookies.forEach(request::cookie);

            HttpClient httpClient = reg.httpClient;

            boolean isFuture = CompletableFuture.class.isAssignableFrom(javaReturnType);
            final Class<Object> methodDeclaringType = context.getDeclaringType();
            if(Publishers.isConvertibleToPublisher(javaReturnType) || isFuture) {
                Argument<?> publisherArgument = returnType.asArgument().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                Class<?> argumentType = publisherArgument.getType();
                Publisher<?> publisher;
                if(HttpResponse.class.isAssignableFrom(argumentType)) {
                    request.accept( context.getValue(Consumes.class,MediaType[].class).orElse(DEFAULT_ACCEPT_TYPES));
                    publisher = httpClient.exchange(
                            request, returnType.asArgument().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT)
                    );
                }
                else if(Void.class.isAssignableFrom(argumentType)) {
                    publisher = httpClient.exchange(
                            request
                    );
                }
                else {
                    MediaType[] acceptTypes = context.getValue(Consumes.class, MediaType[].class).orElse(DEFAULT_ACCEPT_TYPES);
                    request.accept(acceptTypes);
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
                            if(!Void.class.isAssignableFrom(argumentType)) {
                                reference.set(message);
                            }
                        }

                        @Override
                        protected void doOnError(Throwable t) {
                            if(t instanceof HttpClientResponseException) {
                                HttpClientResponseException e = (HttpClientResponseException) t;
                                if( e.getStatus() == HttpStatus.NOT_FOUND) {
                                    future.complete(null);
                                    return;
                                }
                            }
                            if(LOG.isErrorEnabled()) {
                                LOG.error("Client ["+ methodDeclaringType.getName()+"] received HTTP error response: " + t.getMessage(), t);
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
                            new HttpClientException("Cannot convert response publisher to Reactive type (Unsupported Reactive type): " + javaReturnType)
                    );
                    for (ReactiveClientResultTransformer transformer : transformers) {
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
                    } catch (RuntimeException t) {
                        if( t instanceof HttpClientResponseException && ((HttpClientResponseException)t).getStatus() == HttpStatus.NOT_FOUND) {
                            if(javaReturnType == Optional.class) {
                                return Optional.empty();
                            }
                            return null;
                        }
                        else {
                            throw t;
                        }
                    }
                }
            }
        }
        // try other introduction advice
        return context.proceed();
    }

    private ClientRegistration getClient(MethodInvocationContext<Object, Object> context, Client clientAnn) {
        String[] clientId = clientAnn.value();

        return clients.computeIfAbsent(Arrays.hashCode(clientId), integer -> {
            LoadBalancer loadBalancer = loadBalancerResolver.resolve(clientId)
                                                                  .orElseThrow(()->
                                                                          new HttpClientException("Invalid service reference ["+ArrayUtils.toString((Object[]) clientId)+"] specified to @Client")
                                                                  );
            String contextPath = "";
            String path = clientAnn.path();
            if(StringUtils.isNotEmpty(path)) {
                contextPath = path;
            }
            else if(ArrayUtils.isNotEmpty(clientId) && clientId[0].startsWith("/")) {
                contextPath = clientId[0];
            }
            HttpClientConfiguration configuration = beanContext.getBean(clientAnn.configuration());
            HttpClient client = beanContext.createBean(HttpClient.class, loadBalancer, configuration);
            if(client instanceof DefaultHttpClient) {
                DefaultHttpClient defaultClient = (DefaultHttpClient) client;
                defaultClient.setClientIdentifiers(clientId);
                JacksonFeatures jacksonFeatures = context.getAnnotation(JacksonFeatures.class);

                if(jacksonFeatures != null) {
                    Optional<MediaTypeCodec> existingCodec = defaultClient.getMediaTypeCodecRegistry().findCodec(MediaType.APPLICATION_JSON_TYPE);
                    ObjectMapper objectMapper = null;
                    if(existingCodec.isPresent()) {
                        MediaTypeCodec existing = existingCodec.get();
                        if(existing instanceof JsonMediaTypeCodec) {
                            objectMapper = ((JsonMediaTypeCodec) existing).getObjectMapper().copy();
                        }
                    }
                    if(objectMapper == null) {
                        objectMapper = new ObjectMapperFactory().objectMapper(Optional.empty(), Optional.empty());
                    }

                    for (SerializationFeature serializationFeature : jacksonFeatures.enabledSerializationFeatures()) {
                        objectMapper.configure(serializationFeature, true);
                    }

                    for (DeserializationFeature serializationFeature : jacksonFeatures.enabledDeserializationFeatures()) {
                        objectMapper.configure(serializationFeature, true);
                    }

                    for (SerializationFeature serializationFeature : jacksonFeatures.disabledSerializationFeatures()) {
                        objectMapper.configure(serializationFeature, false);
                    }

                    for (DeserializationFeature feature : jacksonFeatures.disabledDeserializationFeatures()) {
                        objectMapper.configure(feature, false);
                    }

                    defaultClient.setMediaTypeCodecRegistry(MediaTypeCodecRegistry.of(new JsonMediaTypeCodec(objectMapper, beanContext.getBean(ApplicationConfiguration.class))));
                }
            }
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
