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

package io.micronaut.http.client.interceptor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.codec.CodecConfiguration;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
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
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.*;
import io.micronaut.http.client.*;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.loadbalance.FixedLoadBalancer;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.http.sse.Event;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jackson.ObjectMapperFactory;
import io.micronaut.jackson.annotation.JacksonFeatures;
import io.micronaut.jackson.codec.JsonMediaTypeCodec;
import io.micronaut.runtime.ApplicationConfiguration;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Introduction advice that implements the {@link Client} annotation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class HttpClientIntroductionAdvice implements MethodInterceptor<Object, Object>, Closeable, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpClient.class);

    /**
     * The default Accept-Types.
     */
    private static final MediaType[] DEFAULT_ACCEPT_TYPES = {MediaType.APPLICATION_JSON_TYPE};

    private final int HEADERS_INITIAL_CAPACITY = 3;
    private final BeanContext beanContext;
    private final Map<String, HttpClient> clients = new ConcurrentHashMap<>();
    private final ReactiveClientResultTransformer[] transformers;
    private final LoadBalancerResolver loadBalancerResolver;
    private final JsonMediaTypeCodec jsonMediaTypeCodec;

    /**
     * Constructor for advice class to setup things like Headers, Cookies, Parameters for Clients.
     *
     * @param beanContext          context to resolve beans
     * @param loadBalancerResolver load balancer resolver
     * @param transformers         transformation classes
     */
    public HttpClientIntroductionAdvice(
        BeanContext beanContext,
        JsonMediaTypeCodec jsonMediaTypeCodec,
        LoadBalancerResolver loadBalancerResolver,
        ReactiveClientResultTransformer... transformers) {

        this.jsonMediaTypeCodec = jsonMediaTypeCodec;
        this.beanContext = beanContext;
        this.loadBalancerResolver = loadBalancerResolver;
        this.transformers = transformers != null ? transformers : new ReactiveClientResultTransformer[0];
    }

    /**
     * Interceptor to apply headers, cookies, parameter and body arguements.
     *
     * @param context The context
     * @return httpClient or future
     */
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        AnnotationValue<Client> clientAnnotation = context.getValues(Client.class).orElseThrow(() ->
                new IllegalStateException("Client advice called from type that is not annotated with @Client: " + context)
        );

        for (MutableArgumentValue<?> argumentValue : context.getParameters().values()) {
            if (argumentValue.getValue() == null && !argumentValue.isAnnotationPresent(Nullable.class)) {
                throw new IllegalArgumentException(
                    String.format("Null values are not allowed to be passed to client methods (%s). Add @javax.validation.Nullable if that is the desired behavior", context.getTargetMethod().toString())
                );
            }
        }

        HttpClient httpClient = getClient(context, clientAnnotation);
        Optional<Class<? extends Annotation>> httpMethodMapping = context.getAnnotationTypeByStereotype(HttpMethodMapping.class);
        if (context.hasStereotype(HttpMethodMapping.class) && httpClient != null) {
            String uri = context.getValue(HttpMethodMapping.class, String.class).orElse("");
            if (StringUtils.isEmpty(uri)) {
                uri = "/" + context.getMethodName();
            }

            Class<? extends Annotation> annotationType = httpMethodMapping.get();

            HttpMethod httpMethod = HttpMethod.valueOf(annotationType.getSimpleName().toUpperCase());

            ReturnType returnType = context.getReturnType();
            Class<?> javaReturnType = returnType.getType();

            UriMatchTemplate uriTemplate = UriMatchTemplate.of("");
            if (!(uri.length() == 1 && uri.charAt(0) == '/')) {
                uriTemplate = uriTemplate.nest(uri);
            }

            Map<String, Object> paramMap = context.getParameterValueMap();
            List<String> uriVariables = uriTemplate.getVariables();

            boolean variableSatisfied = uriVariables.isEmpty() || uriVariables.containsAll(paramMap.keySet());
            MutableHttpRequest<Object> request;
            Object body = null;
            Map<String, MutableArgumentValue<?>> parameters = context.getParameters();
            Argument[] arguments = context.getArguments();


            Map<String, String> headers = new LinkedHashMap<>(HEADERS_INITIAL_CAPACITY);

            List<AnnotationValue<Header>> headerAnnotations = context.getAnnotationValuesByType(Header.class);
            for (AnnotationValue<Header> headerAnnotation : headerAnnotations) {
                String headerName = headerAnnotation.get("name", String.class).orElse(null);
                String headerValue = headerAnnotation.getValue(String.class).orElse(null);
                if (StringUtils.isNotEmpty(headerName) && StringUtils.isNotEmpty(headerValue)) {
                    headers.put(headerName, headerValue);
                }
            }

            List<NettyCookie> cookies = new ArrayList<>();
            List<Argument> bodyArguments = new ArrayList<>();
            for (Argument argument : arguments) {
                String argumentName = argument.getName();
                AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
                if (argument.isAnnotationPresent(Body.class)) {
                    body = parameters.get(argumentName).getValue();
                    break;
                } else if (annotationMetadata.isAnnotationPresent(Header.class)) {

                    String headerName = annotationMetadata.getValue(Header.class, String.class).orElse(null);
                    if (StringUtils.isEmpty(headerName)) {
                        headerName = NameUtils.hyphenate(argumentName);
                    }
                    MutableArgumentValue<?> value = parameters.get(argumentName);
                    String finalHeaderName = headerName;
                    ConversionService.SHARED.convert(value.getValue(), String.class)
                        .ifPresent(o -> headers.put(finalHeaderName, o));
                } else if (annotationMetadata.isAnnotationPresent(CookieValue.class)) {
                    Object cookieValue = parameters.get(argumentName).getValue();
                    String cookieName = annotationMetadata.getValue(CookieValue.class, String.class).orElse(null);
                    if (StringUtils.isEmpty(cookieName)) {
                        cookieName = argumentName;
                    }
                    String finalCookieName = cookieName;

                    ConversionService.SHARED.convert(cookieValue, String.class)
                        .ifPresent(o -> cookies.add(new NettyCookie(finalCookieName, o)));

                } else if (annotationMetadata.isAnnotationPresent(QueryValue.class)) {
                    String parameterName = annotationMetadata.getValue(QueryValue.class, String.class).orElse(null);
                    if (!StringUtils.isEmpty(parameterName)) {
                        MutableArgumentValue<?> value = parameters.get(argumentName);
                        ConversionService.SHARED.convert(value.getValue(), String.class)
                            .ifPresent(o -> paramMap.put(parameterName, o));
                    }
                } else if (!uriVariables.contains(argumentName)) {
                    bodyArguments.add(argument);
                }
            }
            if (HttpMethod.permitsRequestBody(httpMethod)) {
                if (body == null && !bodyArguments.isEmpty()) {
                    Map<String, Object> bodyMap = new LinkedHashMap<>();

                    for (Argument bodyArgument : bodyArguments) {
                        String argumentName = bodyArgument.getName();
                        MutableArgumentValue<?> value = parameters.get(argumentName);
                        bodyMap.put(argumentName, value.getValue());
                    }
                    body = bodyMap;
                }

                if (body != null) {
                    if (!variableSatisfied) {

                        if (body instanceof Map) {
                            paramMap.putAll((Map) body);
                            uri = uriTemplate.expand(paramMap);
                            request = HttpRequest.create(httpMethod, uri);
                        } else {
                            BeanMap<Object> beanMap = BeanMap.of(body);
                            for (Map.Entry<String, Object> entry : beanMap.entrySet()) {
                                String k = entry.getKey();
                                Object v = entry.getValue();
                                if (v != null) {
                                    paramMap.put(k, v);
                                }
                            }
                            uri = uriTemplate.expand(paramMap);
                            request = HttpRequest.create(httpMethod, uri);
                        }
                    } else {
                        uri = uriTemplate.expand(paramMap);
                        request = HttpRequest.create(httpMethod, uri);
                    }
                    request.body(body);
                } else {
                    uri = uriTemplate.expand(paramMap);
                    request = HttpRequest.create(httpMethod, uri);
                }
            } else {
                uri = uriTemplate.expand(paramMap);
                request = HttpRequest.create(httpMethod, uri);
            }

            // Set the URI template used to make the request for tracing purposes
            request.setAttribute(HttpAttributes.URI_TEMPLATE, resolveTemplate(clientAnnotation, uriTemplate.toString()));
            String serviceId = clientAnnotation.getValue(String.class).orElse(null);
            request.setAttribute(HttpAttributes.SERVICE_ID, serviceId);


            if (!headers.isEmpty()) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    request.header(entry.getKey(), entry.getValue());
                }
            }

            cookies.forEach(request::cookie);

            boolean isFuture = CompletableFuture.class.isAssignableFrom(javaReturnType);
            final Class<Object> methodDeclaringType = context.getDeclaringType();
            if (Publishers.isConvertibleToPublisher(javaReturnType) || isFuture) {
                boolean isSingle = Publishers.isSingle(javaReturnType) || isFuture || context.getValue(Produces.class, "single", Boolean.class).orElse(false);
                Argument<?> publisherArgument = returnType.asArgument().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);


                Class<?> argumentType = publisherArgument.getType();

                if (HttpResponse.class.isAssignableFrom(argumentType) || HttpStatus.class.isAssignableFrom(argumentType)) {
                    isSingle = true;
                }


                Publisher<?> publisher;

                MediaType[] contentTypes = context.getValue(Consumes.class, MediaType[].class).orElse(DEFAULT_ACCEPT_TYPES);
                if (ArrayUtils.isNotEmpty(contentTypes) && HttpMethod.permitsRequestBody(request.getMethod())) {
                    request.contentType(contentTypes[0]);
                }

                if (!isSingle && httpClient instanceof StreamingHttpClient) {
                    StreamingHttpClient streamingHttpClient = (StreamingHttpClient) httpClient;
                    if (HttpResponse.class.isAssignableFrom(argumentType)) {
                        request.accept(context.getValue(Produces.class, MediaType[].class).orElse(DEFAULT_ACCEPT_TYPES));
                        publisher = streamingHttpClient.exchangeStream(
                                request
                        );
                    } else if (Void.class.isAssignableFrom(argumentType)) {
                        publisher = streamingHttpClient.exchangeStream(
                                request
                        );
                    } else {
                        MediaType[] acceptTypes = context.getValue(Produces.class, MediaType[].class).orElse(DEFAULT_ACCEPT_TYPES);
                        request.accept(acceptTypes);

                        boolean isEventStream = Arrays.stream(acceptTypes).anyMatch(mediaType -> mediaType.equals(MediaType.TEXT_EVENT_STREAM_TYPE));

                        if (isEventStream && streamingHttpClient instanceof SseClient) {
                            SseClient sseClient = (SseClient) streamingHttpClient;
                            if (publisherArgument.getType() == Event.class) {
                                publisher = sseClient.eventStream(
                                        request, publisherArgument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT)
                                );
                            } else {
                                publisher = Flowable.fromPublisher(sseClient.eventStream(
                                        request, publisherArgument
                                )).map(Event::getData);
                            }
                        } else {
                            boolean isJson = Arrays.stream(acceptTypes).anyMatch(mediaType -> mediaType.getExtension().equals("json") || jsonMediaTypeCodec.getMediaTypes().contains(mediaType));
                            if (isJson) {
                                publisher = streamingHttpClient.jsonStream(
                                        request, publisherArgument
                                );
                            } else {
                                publisher = streamingHttpClient.dataStream(
                                        request
                                );
                            }
                        }
                    }

                } else {

                    if (HttpResponse.class.isAssignableFrom(argumentType)) {
                        request.accept(context.getValue(Produces.class, MediaType[].class).orElse(DEFAULT_ACCEPT_TYPES));
                        publisher = httpClient.exchange(
                                request, publisherArgument
                        );
                    } else if (Void.class.isAssignableFrom(argumentType)) {
                        publisher = httpClient.exchange(
                                request
                        );
                    } else {
                        MediaType[] acceptTypes = context.getValue(Produces.class, MediaType[].class).orElse(DEFAULT_ACCEPT_TYPES);
                        request.accept(acceptTypes);

                        publisher = httpClient.retrieve(
                                request, publisherArgument
                        );
                    }
                }

                if (isFuture) {
                    CompletableFuture<Object> future = new CompletableFuture<>();
                    publisher.subscribe(new CompletionAwareSubscriber<Object>() {
                        AtomicReference<Object> reference = new AtomicReference<>();

                        @Override
                        protected void doOnSubscribe(Subscription subscription) {
                            subscription.request(1);
                        }

                        @Override
                        protected void doOnNext(Object message) {
                            if (!Void.class.isAssignableFrom(argumentType)) {
                                reference.set(message);
                            }
                        }

                        @Override
                        protected void doOnError(Throwable t) {
                            if (t instanceof HttpClientResponseException) {
                                HttpClientResponseException e = (HttpClientResponseException) t;
                                if (e.getStatus() == HttpStatus.NOT_FOUND) {
                                    future.complete(null);
                                    return;
                                }
                            }
                            if (LOG.isErrorEnabled()) {
                                LOG.error("Client [" + methodDeclaringType.getName() + "] received HTTP error response: " + t.getMessage(), t);
                            }

                            future.completeExceptionally(t);
                        }

                        @Override
                        protected void doOnComplete() {
                            future.complete(reference.get());
                        }
                    });
                    return future;
                } else {
                    Object finalPublisher = ConversionService.SHARED.convert(publisher, javaReturnType).orElseThrow(() ->
                        new HttpClientException("Cannot convert response publisher to Reactive type (Unsupported Reactive type): " + javaReturnType)
                    );
                    for (ReactiveClientResultTransformer transformer : transformers) {
                        finalPublisher = transformer.transform(finalPublisher);
                    }
                    return finalPublisher;
                }
            } else {
                BlockingHttpClient blockingHttpClient = httpClient.toBlocking();
                if (HttpResponse.class.isAssignableFrom(javaReturnType)) {
                    return blockingHttpClient.exchange(
                        request, returnType.asArgument().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT)
                    );
                } else if (void.class == javaReturnType) {
                    blockingHttpClient.exchange(request);
                    return null;
                } else {
                    try {
                        return blockingHttpClient.retrieve(
                            request, returnType.asArgument()
                        );
                    } catch (RuntimeException t) {
                        if (t instanceof HttpClientResponseException && ((HttpClientResponseException) t).getStatus() == HttpStatus.NOT_FOUND) {
                            if (javaReturnType == Optional.class) {
                                return Optional.empty();
                            }
                            return null;
                        } else {
                            throw t;
                        }
                    }
                }
            }
        }
        // try other introduction advice
        return context.proceed();
    }

    /**
     * Resolve the template for the client annotation.
     *
     * @param clientAnnotation client annotation reference
     * @param templateString   template to be applied
     * @return resolved template contents
     */
    private String resolveTemplate(AnnotationValue<Client> clientAnnotation, String templateString) {
        String path = clientAnnotation.get("path", String.class).orElse(null);
        if (StringUtils.isNotEmpty(path)) {
            return path + templateString;
        } else {
            String value = clientAnnotation.getValue(String.class).orElse(null);
            if (StringUtils.isNotEmpty(value)) {
                if (value.startsWith("/")) {
                    return value + templateString;
                }
            }
            return templateString;
        }
    }

    /**
     * Gets the client registration for the http request.
     *
     * @param context   application contextx
     * @param clientAnn client annotation
     * @return client registration
     */
    private HttpClient getClient(MethodInvocationContext<Object, Object> context, AnnotationValue<Client> clientAnn) {
        String clientId = clientAnn.getValue(String.class).orElse(null);
        if (StringUtils.isEmpty(clientId)) {
            return null;
        }

        return clients.computeIfAbsent(clientId, integer -> {
            LoadBalancer loadBalancer = loadBalancerResolver.resolve(clientId)
                .orElseThrow(() ->
                    new HttpClientException("Invalid service reference [" + clientId + "] specified to @Client")
                );
            String contextPath = null;
            String path = clientAnn.get("path", String.class).orElse(null);
            if (StringUtils.isNotEmpty(path)) {
                contextPath = path;
            } else if (StringUtils.isNotEmpty(clientId) && clientId.startsWith("/")) {
                contextPath = clientId;
            } else {
                if (loadBalancer instanceof FixedLoadBalancer) {
                    contextPath = ((FixedLoadBalancer) loadBalancer).getUrl().getPath();
                }
            }

            HttpClientConfiguration configuration;
            Optional<HttpClientConfiguration> clientSpecificConfig = beanContext.findBean(
                HttpClientConfiguration.class,
                Qualifiers.byName(clientId)
            );
            Class<HttpClientConfiguration> defaultConfiguration = clientAnn.get("configuration", Class.class).orElse(HttpClientConfiguration.class);
            configuration = clientSpecificConfig.orElseGet(() -> beanContext.getBean(defaultConfiguration));
            HttpClient client = beanContext.createBean(HttpClient.class, loadBalancer, configuration, contextPath);
            if (client instanceof DefaultHttpClient) {
                DefaultHttpClient defaultClient = (DefaultHttpClient) client;
                defaultClient.setClientIdentifiers(clientId);
                AnnotationValue<JacksonFeatures> jacksonFeatures = context.getValues(JacksonFeatures.class).orElse(null);

                if (jacksonFeatures != null) {
                    Optional<MediaTypeCodec> existingCodec = defaultClient.getMediaTypeCodecRegistry().findCodec(MediaType.APPLICATION_JSON_TYPE);
                    ObjectMapper objectMapper = null;
                    if (existingCodec.isPresent()) {
                        MediaTypeCodec existing = existingCodec.get();
                        if (existing instanceof JsonMediaTypeCodec) {
                            objectMapper = ((JsonMediaTypeCodec) existing).getObjectMapper().copy();
                        }
                    }
                    if (objectMapper == null) {
                        objectMapper = new ObjectMapperFactory().objectMapper(Optional.empty(), Optional.empty());
                    }

                    SerializationFeature[] enabledSerializationFeatures = jacksonFeatures.get("enabledSerializationFeatures", SerializationFeature[].class).orElse(null);
                    if (enabledSerializationFeatures != null) {
                        for (SerializationFeature serializationFeature : enabledSerializationFeatures) {
                            objectMapper.configure(serializationFeature, true);
                        }
                    }

                    DeserializationFeature[] enabledDeserializationFeatures = jacksonFeatures.get("enabledDeserializationFeatures", DeserializationFeature[].class).orElse(null);

                    if (enabledDeserializationFeatures != null) {
                        for (DeserializationFeature serializationFeature : enabledDeserializationFeatures) {
                            objectMapper.configure(serializationFeature, true);
                        }
                    }

                    SerializationFeature[] disabledSerializationFeatures = jacksonFeatures.get("disabledSerializationFeatures", SerializationFeature[].class).orElse(null);
                    if (disabledSerializationFeatures != null) {
                        for (SerializationFeature serializationFeature : disabledSerializationFeatures) {
                            objectMapper.configure(serializationFeature, false);
                        }
                    }

                    DeserializationFeature[] disabledDeserializationFeatures = jacksonFeatures.get("disabledDeserializationFeatures", DeserializationFeature[].class).orElse(null);

                    if (disabledDeserializationFeatures != null) {
                        for (DeserializationFeature feature : disabledDeserializationFeatures) {
                            objectMapper.configure(feature, false);
                        }
                    }

                    defaultClient.setMediaTypeCodecRegistry(
                            MediaTypeCodecRegistry.of(
                                    new JsonMediaTypeCodec(objectMapper,
                                            beanContext.getBean(ApplicationConfiguration.class),
                                            beanContext.findBean(CodecConfiguration.class, Qualifiers.byName(JsonMediaTypeCodec.CONFIGURATION_QUALIFIER)).orElse(null))));
                }
            }
            return client;
        });
    }

    /**
     * Cleanup method to prevent resource leaking.
     *
     */
    @Override
    @PreDestroy
    public void close() {
        for (HttpClient client : clients.values()) {
            client.close();
        }
    }
}
