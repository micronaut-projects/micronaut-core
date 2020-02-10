/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.client.*;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.interceptor.configuration.ClientVersioningConfiguration;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.sse.Event;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriMatchVariable;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.jackson.codec.JsonMediaTypeCodec;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Introduction advice that implements the {@link Client} annotation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Internal
@BootstrapContextCompatible
public class HttpClientIntroductionAdvice implements MethodInterceptor<Object, Object> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpClient.class);

    /**
     * The default Accept-Types.
     */
    private static final MediaType[] DEFAULT_ACCEPT_TYPES = {MediaType.APPLICATION_JSON_TYPE};

    private static final int HEADERS_INITIAL_CAPACITY = 3;
    private static final int ATTRIBUTES_INITIAL_CAPACITY = 1;
    private final BeanContext beanContext;
    private final Map<String, ClientVersioningConfiguration> versioningConfigurations = new ConcurrentHashMap<>(5);
    private final List<ReactiveClientResultTransformer> transformers;
    private final JsonMediaTypeCodec jsonMediaTypeCodec;
    private final RxHttpClientRegistry clientFactory;

    /**
     * Constructor for advice class to setup things like Headers, Cookies, Parameters for Clients.
     *
     * @param beanContext          context to resolve beans
     * @param clientFactory        The client factory
     * @param jsonMediaTypeCodec   The JSON media type codec
     * @param transformers         transformation classes
     */
    @Inject
    public HttpClientIntroductionAdvice(
            BeanContext beanContext,
            RxHttpClientRegistry clientFactory,
            JsonMediaTypeCodec jsonMediaTypeCodec,
            List<ReactiveClientResultTransformer> transformers) {

        this.clientFactory = clientFactory;
        this.jsonMediaTypeCodec = jsonMediaTypeCodec;
        this.beanContext = beanContext;
        this.transformers = transformers != null ? transformers : Collections.emptyList();
    }

    /**
     * Interceptor to apply headers, cookies, parameter and body arguements.
     *
     * @param context The context
     * @return httpClient or future
     */
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        if (!context.hasAnnotation(Client.class)) {
            throw new IllegalStateException("Client advice called from type that is not annotated with @Client: " + context);
        }

        final AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();
        HttpClient httpClient = clientFactory.getClient(annotationMetadata);

        Class<?> declaringType = context.getDeclaringType();
        if (Closeable.class == declaringType || AutoCloseable.class == declaringType) {
            clientFactory.disposeClient(annotationMetadata);
            return null;
        }

        Optional<Class<? extends Annotation>> httpMethodMapping = context.getAnnotationTypeByStereotype(HttpMethodMapping.class);
        if (context.hasStereotype(HttpMethodMapping.class) && httpClient != null) {
            AnnotationValue<HttpMethodMapping> mapping = context.getAnnotation(HttpMethodMapping.class);
            String uri = mapping.getRequiredValue(String.class);
            if (StringUtils.isEmpty(uri)) {
                uri = "/" + context.getMethodName();
            }

            Class<? extends Annotation> annotationType = httpMethodMapping.get();

            HttpMethod httpMethod = HttpMethod.parse(annotationType.getSimpleName().toUpperCase());
            String httpMethodName = context.stringValue(CustomHttpMethod.class, "method").orElse(httpMethod.name());

            ReturnType returnType = context.getReturnType();
            Class<?> javaReturnType = returnType.getType();

            UriMatchTemplate uriTemplate = UriMatchTemplate.of("");
            if (!(uri.length() == 1 && uri.charAt(0) == '/')) {
                uriTemplate = uriTemplate.nest(uri);
            }

            Map<String, Object> paramMap = context.getParameterValueMap();
            Map<String, String> queryParams = new LinkedHashMap<>();
            List<String> uriVariables = uriTemplate.getVariableNames();

            MutableHttpRequest<Object> request;
            Object body = null;
            Map<String, MutableArgumentValue<?>> parameters = context.getParameters();
            Argument[] arguments = context.getArguments();


            Map<String, String> headers = new LinkedHashMap<>(HEADERS_INITIAL_CAPACITY);

            List<AnnotationValue<Header>> headerAnnotations = context.getAnnotationValuesByType(Header.class);
            for (AnnotationValue<Header> headerAnnotation : headerAnnotations) {
                String headerName = headerAnnotation.stringValue("name").orElse(null);
                String headerValue = headerAnnotation.stringValue().orElse(null);
                if (StringUtils.isNotEmpty(headerName) && StringUtils.isNotEmpty(headerValue)) {
                    headers.putIfAbsent(headerName, headerValue);
                }
            }

            context.findAnnotation(Version.class)
                    .flatMap(AnnotationValue::stringValue)
                    .filter(StringUtils::isNotEmpty)
                    .ifPresent(version -> {

                        ClientVersioningConfiguration configuration = getVersioningConfiguration(annotationMetadata);

                        configuration.getHeaders()
                                .forEach(header -> headers.put(header, version));

                        configuration.getParameters()
                                .forEach(parameter -> queryParams.put(parameter, version));
                    });

            Map<String, Object> attributes = new LinkedHashMap<>(ATTRIBUTES_INITIAL_CAPACITY);

            List<AnnotationValue<RequestAttribute>> attributeAnnotations = context.getAnnotationValuesByType(RequestAttribute.class);
            for (AnnotationValue<RequestAttribute> attributeAnnotation : attributeAnnotations) {
                String attributeName = attributeAnnotation.stringValue("name").orElse(null);
                Object attributeValue = attributeAnnotation.getValue(Object.class).orElse(null);
                if (StringUtils.isNotEmpty(attributeName) && attributeValue != null) {
                    attributes.put(attributeName, attributeValue);
                }
            }

            List<Cookie> cookies = new ArrayList<>();
            List<Argument> bodyArguments = new ArrayList<>();
            ConversionService<?> conversionService = ConversionService.SHARED;
            BasicAuth basicAuth = null;

            for (Argument argument : arguments) {
                String argumentName = argument.getName();
                AnnotationMetadata argumentMetadata = argument.getAnnotationMetadata();
                MutableArgumentValue<?> value = parameters.get(argumentName);
                Object definedValue = value.getValue();

                if (paramMap.containsKey(argumentName)) {
                    if (argumentMetadata.hasStereotype(Format.class)) {
                        final Object v = paramMap.get(argumentName);
                        if (v != null) {
                            paramMap.put(argumentName, conversionService.convert(v, ConversionContext.of(String.class).with(argument.getAnnotationMetadata())));
                        }
                    }
                }
                if (definedValue == null) {
                    definedValue = argument.getAnnotationMetadata().stringValue(Bindable.class, "defaultValue").orElse(null);
                }

                if (definedValue == null && !argument.isNullable()) {
                    throw new IllegalArgumentException(
                            String.format("Argument [%s] is null. Null values are not allowed to be passed to client methods (%s). Add a supported Nullable annotation type if that is the desired behavior", argument.getName(), context.getExecutableMethod().toString())
                    );
                }

                if (argument.isAnnotationPresent(Body.class)) {
                    body = definedValue;
                } else if (argumentMetadata.isAnnotationPresent(Header.class)) {

                    String headerName = argumentMetadata.stringValue(Header.class).orElse(null);
                    if (StringUtils.isEmpty(headerName)) {
                        headerName = NameUtils.hyphenate(argumentName);
                    }
                    String finalHeaderName = headerName;
                    conversionService.convert(definedValue, String.class)
                            .ifPresent(o -> headers.put(finalHeaderName, o));
                } else if (argumentMetadata.isAnnotationPresent(CookieValue.class)) {
                    String cookieName = argumentMetadata.stringValue(CookieValue.class).orElse(null);
                    if (StringUtils.isEmpty(cookieName)) {
                        cookieName = argumentName;
                    }
                    String finalCookieName = cookieName;

                    conversionService.convert(definedValue, String.class)
                            .ifPresent(o -> cookies.add(Cookie.of(finalCookieName, o)));

                } else if (argumentMetadata.isAnnotationPresent(QueryValue.class)) {
                    String parameterName = argumentMetadata.stringValue(QueryValue.class).orElse(null);
                    boolean isExploded = uriTemplate.getVariables()
                            .stream()
                            .filter(v -> v.getName().equals(parameterName))
                            .findFirst()
                            .map(UriMatchVariable::isExploded).orElse(false);

                    if (isExploded) {
                        if (!StringUtils.isEmpty(parameterName)) {
                            paramMap.put(parameterName, definedValue);
                        }
                    } else {
                        conversionService.convert(definedValue, ConversionContext.of(String.class).with(argumentMetadata)).ifPresent(o -> {
                            if (!StringUtils.isEmpty(parameterName)) {
                                paramMap.put(parameterName, o);
                                queryParams.put(parameterName, o);
                            } else {
                                queryParams.put(argumentName, o);
                            }
                        });
                    }

                } else if (argumentMetadata.isAnnotationPresent(RequestAttribute.class)) {
                    String attributeName = argumentMetadata.stringValue(RequestAttribute.class).orElse(null);
                    if (StringUtils.isEmpty(attributeName)) {
                        attributeName = NameUtils.hyphenate(argumentName);
                    }
                    String finalAttributeName = attributeName;
                    conversionService.convert(definedValue, Object.class)
                            .ifPresent(o -> attributes.put(finalAttributeName, o));
                } else if (argumentMetadata.isAnnotationPresent(PathVariable.class)) {
                    String parameterName = argumentMetadata.stringValue(PathVariable.class).orElse(null);
                    conversionService.convert(definedValue, ConversionContext.of(String.class).with(argumentMetadata)).ifPresent(o -> {
                        if (!StringUtils.isEmpty(o)) {
                            paramMap.put(parameterName, o);
                        }
                    });
                } else if (argument.getType() == BasicAuth.class) {
                    basicAuth = (BasicAuth) paramMap.get(argument.getName());
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
                    boolean variableSatisfied = uriVariables.isEmpty() || uriVariables.containsAll(paramMap.keySet());
                    if (!variableSatisfied) {
                        if (body instanceof Map) {
                            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) body).entrySet()) {
                                String k = entry.getKey().toString();
                                Object v = entry.getValue();
                                if (v != null) {
                                    paramMap.putIfAbsent(k, v);
                                }
                            }
                        } else {
                            BeanMap<Object> beanMap = BeanMap.of(body);
                            for (Map.Entry<String, Object> entry : beanMap.entrySet()) {
                                String k = entry.getKey();
                                Object v = entry.getValue();
                                if (v != null) {
                                    paramMap.putIfAbsent(k, v);
                                }
                            }
                        }
                    }
                }
            }

            uri = uriTemplate.expand(paramMap);
            uriVariables.forEach(queryParams::remove);

            request = HttpRequest.create(httpMethod, appendQuery(uri, queryParams), httpMethodName);

            if (body != null) {
                request.body(body);

                MediaType[] contentTypes = MediaType.of(context.stringValues(Produces.class));
                if (ArrayUtils.isEmpty(contentTypes)) {
                    contentTypes = DEFAULT_ACCEPT_TYPES;
                }
                if (ArrayUtils.isNotEmpty(contentTypes)) {
                    request.contentType(contentTypes[0]);
                }
            }

            request.setAttribute(HttpAttributes.INVOCATION_CONTEXT, context);
            // Set the URI template used to make the request for tracing purposes
            request.setAttribute(HttpAttributes.URI_TEMPLATE, resolveTemplate(annotationMetadata, uriTemplate.toString()));
            String serviceId = getClientId(annotationMetadata);
            Argument<?> errorType = annotationMetadata.classValue(Client.class, "errorType")
                    .map((Function<Class, Argument>) Argument::of).orElse(HttpClient.DEFAULT_ERROR_TYPE);
            request.setAttribute(HttpAttributes.SERVICE_ID, serviceId);


            if (!headers.isEmpty()) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    request.header(entry.getKey(), entry.getValue());
                }
            }

            cookies.forEach(request::cookie);

            if (!attributes.isEmpty()) {
                for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                    request.setAttribute(entry.getKey(), entry.getValue());
                }
            }

            MediaType[] acceptTypes = MediaType.of(context.stringValues(Consumes.class));
            if (ArrayUtils.isEmpty(acceptTypes)) {
                acceptTypes = DEFAULT_ACCEPT_TYPES;
            }

            if (basicAuth != null) {
                request.basicAuth(basicAuth.getUsername(), basicAuth.getPassword());
            }

            boolean isFuture = CompletionStage.class.isAssignableFrom(javaReturnType);
            final Class<?> methodDeclaringType = declaringType;
            if (Publishers.isConvertibleToPublisher(javaReturnType) || isFuture) {
                boolean isSingle = Publishers.isSingle(javaReturnType) || isFuture || context.isTrue(Consumes.class, "single");
                Argument<?> publisherArgument = returnType.asArgument().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);


                Class<?> argumentType = publisherArgument.getType();

                if (HttpResponse.class.isAssignableFrom(argumentType) || HttpStatus.class.isAssignableFrom(argumentType)) {
                    isSingle = true;
                }

                Publisher<?> publisher;

                if (!isSingle && httpClient instanceof StreamingHttpClient) {
                    StreamingHttpClient streamingHttpClient = (StreamingHttpClient) httpClient;

                    if (!Void.class.isAssignableFrom(argumentType)) {
                        request.accept(acceptTypes);
                    }

                    if (HttpResponse.class.isAssignableFrom(argumentType) ||
                            Void.class.isAssignableFrom(argumentType)) {
                        publisher = streamingHttpClient.exchangeStream(
                                request
                        );
                    } else {
                        boolean isEventStream = Arrays.asList(acceptTypes).contains(MediaType.TEXT_EVENT_STREAM_TYPE);

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
                            boolean isJson = isJsonParsedMediaType(acceptTypes);
                            if (isJson) {
                                publisher = streamingHttpClient.jsonStream(
                                        request, publisherArgument
                                );
                            } else {
                                Publisher<ByteBuffer<?>> byteBufferPublisher = streamingHttpClient.dataStream(
                                        request
                                );
                                if (argumentType == ByteBuffer.class) {
                                    publisher = byteBufferPublisher;
                                } else {
                                    if (conversionService.canConvert(ByteBuffer.class, argumentType)) {
                                        // It would be nice if we could capture the TypeConverter here
                                        publisher = Flowable.fromPublisher(byteBufferPublisher)
                                                .map(value -> conversionService.convert(value, argumentType).get());
                                    } else {
                                        throw new ConfigurationException("Cannot create the generated HTTP client's " +
                                                "required return type, since no TypeConverter from ByteBuffer to " +
                                                argumentType + " is registered");
                                    }
                                }

                            }
                        }
                    }

                } else {

                    if (Void.class.isAssignableFrom(argumentType) || Completable.class.isAssignableFrom(javaReturnType)) {
                        publisher = httpClient.exchange(
                                request, null, errorType
                        );
                    } else {
                        request.accept(acceptTypes);
                        if (HttpResponse.class.isAssignableFrom(argumentType)) {
                            publisher = httpClient.exchange(
                                    request, publisherArgument, errorType
                            );
                        } else {
                            publisher = httpClient.retrieve(
                                    request, publisherArgument, errorType
                            );
                        }
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
                    Object finalPublisher = conversionService.convert(publisher, javaReturnType).orElseThrow(() ->
                            new HttpClientException("Cannot convert response publisher to Reactive type (Unsupported Reactive type): " + javaReturnType)
                    );
                    for (ReactiveClientResultTransformer transformer : transformers) {
                        finalPublisher = transformer.transform(finalPublisher);
                    }
                    return finalPublisher;
                }
            } else {
                BlockingHttpClient blockingHttpClient = httpClient.toBlocking();

                if (void.class != javaReturnType) {
                    request.accept(acceptTypes);
                }

                if (HttpResponse.class.isAssignableFrom(javaReturnType)) {
                    return handleBlockingCall(javaReturnType, () ->
                            blockingHttpClient.exchange(request,
                                    returnType.asArgument().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT),
                                    errorType
                            ));
                } else if (void.class == javaReturnType) {
                    return handleBlockingCall(javaReturnType, () ->
                            blockingHttpClient.exchange(request, null, errorType));
                } else {
                    return handleBlockingCall(javaReturnType, () ->
                            blockingHttpClient.retrieve(request, returnType.asArgument(), errorType));
                }
            }
        }
        // try other introduction advice
        return context.proceed();
    }

    private Object handleBlockingCall(Class returnType, Supplier<Object> supplier) {
        try {
            if (void.class == returnType) {
                supplier.get();
                return null;
            } else {
                return supplier.get();
            }
        } catch (RuntimeException t) {
            if (t instanceof HttpClientResponseException && ((HttpClientResponseException) t).getStatus() == HttpStatus.NOT_FOUND) {
                if (returnType == Optional.class) {
                    return Optional.empty();
                } else if (HttpResponse.class.isAssignableFrom(returnType)) {
                    return ((HttpClientResponseException) t).getResponse();
                }
                return null;
            } else {
                throw t;
            }
        }
    }

    private ClientVersioningConfiguration getVersioningConfiguration(AnnotationMetadata annotationMetadata) {
        return versioningConfigurations.computeIfAbsent(getClientId(annotationMetadata), clientId ->
                beanContext.findBean(ClientVersioningConfiguration.class, Qualifiers.byName(clientId))
                        .orElseGet(() -> beanContext.findBean(ClientVersioningConfiguration.class, Qualifiers.byName(ClientVersioningConfiguration.DEFAULT))
                                .orElseThrow(() -> new ConfigurationException("Attempt to apply a '@Version' to the request, but " +
                                        "versioning configuration found neither for '" + clientId + "' nor '" + ClientVersioningConfiguration.DEFAULT + "' provided.")
                                )));

    }

    private boolean isJsonParsedMediaType(MediaType[] acceptTypes) {
        return Arrays.stream(acceptTypes).anyMatch(mediaType ->
                mediaType.equals(MediaType.APPLICATION_JSON_STREAM_TYPE) ||
                        mediaType.getExtension().equals(MediaType.EXTENSION_JSON) ||
                        jsonMediaTypeCodec.getMediaTypes().contains(mediaType)
        );
    }

    /**
     * Resolve the template for the client annotation.
     *
     * @param annotationMetadata client annotation reference
     * @param templateString   template to be applied
     * @return resolved template contents
     */
    private String resolveTemplate(AnnotationMetadata annotationMetadata, String templateString) {
        String path = annotationMetadata.stringValue(Client.class, "path").orElse(null);
        if (StringUtils.isNotEmpty(path)) {
            return path + templateString;
        } else {
            String value = getClientId(annotationMetadata);
            if (StringUtils.isNotEmpty(value)) {
                if (value.startsWith("/")) {
                    return value + templateString;
                }
            }
            return templateString;
        }
    }

    private String getClientId(AnnotationMetadata clientAnn) {
        return clientAnn.stringValue(Client.class).orElse(null);
    }

    private String appendQuery(String uri, Map<String, String> queryParams) {
        if (!queryParams.isEmpty()) {
            final UriBuilder builder = UriBuilder.of(uri);
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
            return builder.toString();
        }
        return uri;
    }
}
