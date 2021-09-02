/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.interceptor;

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.async.subscriber.CompletionAwareSubscriber;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.CustomHttpMethod;
import io.micronaut.http.annotation.HttpMethodMapping;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.ReactiveClientResultTransformer;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.bind.ClientArgumentRequestBinder;
import io.micronaut.http.client.bind.ClientRequestUriContext;
import io.micronaut.http.client.bind.HttpClientBinderRegistry;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.sse.Event;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.json.codec.JsonMediaTypeCodec;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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

    private static final Logger LOG = LoggerFactory.getLogger(HttpClientIntroductionAdvice.class);

    /**
     * The default Accept-Types.
     */
    private static final MediaType[] DEFAULT_ACCEPT_TYPES = {MediaType.APPLICATION_JSON_TYPE};

    private static final int HEADERS_INITIAL_CAPACITY = 3;
    private static final int ATTRIBUTES_INITIAL_CAPACITY = 1;
    private final List<ReactiveClientResultTransformer> transformers;
    private final HttpClientBinderRegistry binderRegistry;
    private final JsonMediaTypeCodec jsonMediaTypeCodec;
    private final HttpClientRegistry<?> clientFactory;
    private final ConversionService<?> conversionService;

    /**
     * Constructor for advice class to setup things like Headers, Cookies, Parameters for Clients.
     *
     * @param clientFactory        The client factory
     * @param jsonMediaTypeCodec   The JSON media type codec
     * @param transformers         transformation classes
     * @param binderRegistry       The client binder registry
     * @param conversionService    The bean conversion context
     */
    public HttpClientIntroductionAdvice(
            HttpClientRegistry<?> clientFactory,
            JsonMediaTypeCodec jsonMediaTypeCodec,
            List<ReactiveClientResultTransformer> transformers,
            HttpClientBinderRegistry binderRegistry,
            ConversionService<?> conversionService) {
        this.clientFactory = clientFactory;
        this.jsonMediaTypeCodec = jsonMediaTypeCodec;
        this.transformers = transformers != null ? transformers : Collections.emptyList();
        this.binderRegistry = binderRegistry;
        this.conversionService = conversionService;
    }

    /**
     * Interceptor to apply headers, cookies, parameter and body arguements.
     *
     * @param context The context
     * @return httpClient or future
     */
    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        if (!context.hasStereotype(Client.class)) {
            throw new IllegalStateException("Client advice called from type that is not annotated with @Client: " + context);
        }

        final AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();

        Class<?> declaringType = context.getDeclaringType();
        if (Closeable.class == declaringType || AutoCloseable.class == declaringType) {
            clientFactory.disposeClient(annotationMetadata);
            return null;
        }

        Optional<Class<? extends Annotation>> httpMethodMapping = context.getAnnotationTypeByStereotype(HttpMethodMapping.class);
        HttpClient httpClient = clientFactory.getClient(annotationMetadata);
        if (context.hasStereotype(HttpMethodMapping.class) && httpClient != null) {
            AnnotationValue<HttpMethodMapping> mapping = context.getAnnotation(HttpMethodMapping.class);
            String uri = mapping.getRequiredValue(String.class);
            if (StringUtils.isEmpty(uri)) {
                uri = "/" + context.getMethodName();
            }

            Class<? extends Annotation> annotationType = httpMethodMapping.get();
            HttpMethod httpMethod = HttpMethod.parse(annotationType.getSimpleName().toUpperCase(Locale.ENGLISH));
            String httpMethodName = context.stringValue(CustomHttpMethod.class, "method").orElse(httpMethod.name());

            MutableHttpRequest<?> request = HttpRequest.create(httpMethod, "", httpMethodName);

            UriMatchTemplate uriTemplate = UriMatchTemplate.of("");
            if (!(uri.length() == 1 && uri.charAt(0) == '/')) {
                uriTemplate = uriTemplate.nest(uri);
            }

            Map<String, Object> pathParams = new HashMap<>();
            Map<String, List<String>> queryParams = new LinkedHashMap<>();
            ClientRequestUriContext uriContext = new ClientRequestUriContext(uriTemplate, pathParams, queryParams);
            List<Argument> bodyArguments = new ArrayList<>();

            List<String> uriVariables = uriTemplate.getVariableNames();
            Map<String, MutableArgumentValue<?>> parameters = context.getParameters();

            ClientArgumentRequestBinder<Object> defaultBinder = (ctx, uriCtx, value, req) -> {
                Argument<?> argument = ctx.getArgument();
                if (uriCtx.getUriTemplate().getVariableNames().contains(argument.getName())) {
                    String name = argument.getAnnotationMetadata().stringValue(Bindable.class)
                            .orElse(argument.getName());
                    // Convert and put as path param
                    if (argument.getAnnotationMetadata().hasStereotype(Format.class)) {
                        ConversionService.SHARED.convert(value,
                                ConversionContext.of(String.class).with(argument.getAnnotationMetadata()))
                                .ifPresent(v -> pathParams.put(name, v));
                    } else {
                        pathParams.put(name, value);
                    }
                } else {
                    bodyArguments.add(ctx.getArgument());
                }
            };

            // Apply all the method binders
            List<Class<? extends Annotation>> methodBinderTypes = context.getAnnotationTypesByStereotype(Bindable.class);
            // @Version is not a bindable, so it needs to looked for separately
            methodBinderTypes.addAll(context.getAnnotationTypesByStereotype(Version.class));
            if (!CollectionUtils.isEmpty(methodBinderTypes)) {
                for (Class<? extends Annotation> binderType : methodBinderTypes) {
                    binderRegistry.findAnnotatedBinder(binderType).ifPresent(b -> b.bind(context, uriContext, request));
                }
            }

            InterceptedMethod interceptedMethod = InterceptedMethod.of(context);

            // Apply all the argument binders
            Argument[] arguments = context.getArguments();
            if (arguments.length > 0) {
                Map<String, Object> paramMap = context.getParameterValueMap();
                for (Argument argument : arguments) {
                    Object definedValue = getValue(argument, context, parameters);

                    if (definedValue != null) {
                        final ClientArgumentRequestBinder<Object> binder = (ClientArgumentRequestBinder<Object>) binderRegistry
                                .findArgumentBinder((Argument<Object>) argument)
                                .orElse(defaultBinder);
                        ArgumentConversionContext conversionContext = ConversionContext.of(argument);
                        binder.bind(conversionContext, uriContext, definedValue, request);
                        if (conversionContext.hasErrors()) {
                            return interceptedMethod.handleException(new ConversionErrorException(argument, conversionContext.getLastError().get()));
                        }
                    }
                }
            }

            Object body = request.getBody().orElse(null);
            if (body == null && !bodyArguments.isEmpty()) {
                Map<String, Object> bodyMap = new LinkedHashMap<>();

                for (Argument bodyArgument : bodyArguments) {
                    String argumentName = bodyArgument.getName();
                    MutableArgumentValue<?> value = parameters.get(argumentName);
                    bodyMap.put(argumentName, value.getValue());
                }
                body = bodyMap;
                request.body(body);
            }

            boolean variableSatisfied = uriVariables.isEmpty() || pathParams.keySet().containsAll(uriVariables);
            if (body != null && !variableSatisfied) {
                if (body instanceof Map) {
                    for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) body).entrySet()) {
                        String k = entry.getKey().toString();
                        Object v = entry.getValue();
                        if (v != null) {
                            pathParams.putIfAbsent(k, v);
                        }
                    }
                } else {
                    BeanMap<Object> beanMap = BeanMap.of(body);
                    for (Map.Entry<String, Object> entry : beanMap.entrySet()) {
                        String k = entry.getKey();
                        Object v = entry.getValue();
                        if (v != null) {
                            pathParams.putIfAbsent(k, v);
                        }
                    }
                }
            }

            if (!HttpMethod.permitsRequestBody(httpMethod)) {
                // If a binder set the body and the method does not permit it, reset to null
                request.body(null);
                body = null;
            }

            uri = uriTemplate.expand(pathParams);
            // Remove all the pathParams that have already been used.
            // Other path parameters are added to query
            uriVariables.forEach(pathParams::remove);
            addParametersToQuery(pathParams, uriContext);

            // The original query can be added by getting it from the request.getUri() and appending
            request.uri(URI.create(appendQuery(uri, uriContext.getQueryParameters())));

            if (body != null && !request.getContentType().isPresent()) {
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


            final MediaType[] acceptTypes;
            Collection<MediaType> accept = request.accept();
            if (accept.isEmpty()) {
                String[] consumesMediaType = context.stringValues(Consumes.class);
                if (ArrayUtils.isEmpty(consumesMediaType)) {
                    acceptTypes = DEFAULT_ACCEPT_TYPES;
                } else {
                    acceptTypes = MediaType.of(consumesMediaType);
                }
                request.accept(acceptTypes);
            } else {
                acceptTypes = accept.toArray(MediaType.EMPTY_ARRAY);
            }

            ReturnType<?> returnType = context.getReturnType();

            try {
                Argument<?> valueType = interceptedMethod.returnTypeValue();
                Class<?> reactiveValueType = valueType.getType();
                switch (interceptedMethod.resultType()) {
                    case PUBLISHER:
                        boolean isSingle = returnType.isSingleResult() ||
                                returnType.isCompletable() ||
                                HttpResponse.class.isAssignableFrom(reactiveValueType) ||
                                HttpStatus.class == reactiveValueType;

                        Publisher<?> publisher;
                        if (!isSingle && httpClient instanceof StreamingHttpClient) {
                            publisher = httpClientResponseStreamingPublisher((StreamingHttpClient) httpClient, acceptTypes, request, valueType);
                        } else {
                            publisher = httpClientResponsePublisher(httpClient, request, returnType, errorType, valueType);
                        }
                        Object finalPublisher = interceptedMethod.handleResult(publisher);
                        for (ReactiveClientResultTransformer transformer : transformers) {
                            finalPublisher = transformer.transform(finalPublisher);
                        }
                        return finalPublisher;
                    case COMPLETION_STAGE:
                        Publisher<?> csPublisher = httpClientResponsePublisher(httpClient, request, returnType, errorType, valueType);
                        CompletableFuture<Object> future = new CompletableFuture<>();
                        csPublisher.subscribe(new CompletionAwareSubscriber<Object>() {
                            AtomicReference<Object> reference = new AtomicReference<>();

                            @Override
                            protected void doOnSubscribe(Subscription subscription) {
                                subscription.request(1);
                            }

                            @Override
                            protected void doOnNext(Object message) {
                                if (Void.class != reactiveValueType) {
                                    reference.set(message);
                                }
                            }

                            @Override
                            protected void doOnError(Throwable t) {
                                if (t instanceof HttpClientResponseException) {
                                    HttpClientResponseException e = (HttpClientResponseException) t;
                                    if (e.getStatus() == HttpStatus.NOT_FOUND) {
                                        if (reactiveValueType == Optional.class) {
                                            future.complete(Optional.empty());
                                        } else if (HttpResponse.class.isAssignableFrom(reactiveValueType)) {
                                            future.complete(e.getResponse());
                                        } else {
                                            future.complete(null);
                                        }
                                        return;
                                    }
                                }
                                if (LOG.isErrorEnabled()) {
                                    LOG.error("Client [" + declaringType.getName() + "] received HTTP error response: " + t.getMessage(), t);
                                }
                                future.completeExceptionally(t);
                            }

                            @Override
                            protected void doOnComplete() {
                                future.complete(reference.get());
                            }
                        });
                        return interceptedMethod.handleResult(future);
                    case SYNCHRONOUS:
                        Class<?> javaReturnType = returnType.getType();
                        BlockingHttpClient blockingHttpClient = httpClient.toBlocking();

                        if (void.class == javaReturnType || httpMethod == HttpMethod.HEAD) {
                            request.getHeaders().remove(HttpHeaders.ACCEPT);
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
                    default:
                        return interceptedMethod.unsupported();
                }
            } catch (Exception e) {
                return interceptedMethod.handleException(e);
            }
        }
        // try other introduction advice
        return context.proceed();
    }

    private Publisher httpClientResponsePublisher(HttpClient httpClient, MutableHttpRequest<?> request,
                                                  ReturnType<?> returnType,
                                                  Argument<?> errorType,
                                                  Argument<?> reactiveValueArgument) {
        Class<?> argumentType = reactiveValueArgument.getType();
        if (Void.class == argumentType || returnType.isVoid()) {
            request.getHeaders().remove(HttpHeaders.ACCEPT);
            return httpClient.exchange(request, Argument.VOID, errorType);
        } else {
            if (HttpResponse.class.isAssignableFrom(argumentType)) {
                return httpClient.exchange(request, reactiveValueArgument, errorType);
            }
            return httpClient.retrieve(request, reactiveValueArgument, errorType);
        }
    }

    private Publisher httpClientResponseStreamingPublisher(StreamingHttpClient streamingHttpClient,
                                                           MediaType[] acceptTypes,
                                                           MutableHttpRequest<?> request,
                                                           Argument<?> reactiveValueArgument) {
        Class<?> reactiveValueType = reactiveValueArgument.getType();
        if (Void.class == reactiveValueType) {
            request.getHeaders().remove(HttpHeaders.ACCEPT);
        }

        if (streamingHttpClient instanceof SseClient && Arrays.asList(acceptTypes).contains(MediaType.TEXT_EVENT_STREAM_TYPE)) {
            SseClient sseClient = (SseClient) streamingHttpClient;
            if (reactiveValueArgument.getType() == Event.class) {
                return sseClient.eventStream(
                        request, reactiveValueArgument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT)
                );
            }
            return Publishers.map(sseClient.eventStream(request, reactiveValueArgument), Event::getData);
        } else {
            if (isJsonParsedMediaType(acceptTypes)) {
                return streamingHttpClient.jsonStream(request, reactiveValueArgument);
            } else {
                Publisher<ByteBuffer<?>> byteBufferPublisher = streamingHttpClient.dataStream(request);
                if (reactiveValueType == ByteBuffer.class) {
                    return byteBufferPublisher;
                } else {
                    if (ConversionService.SHARED.canConvert(ByteBuffer.class, reactiveValueType)) {
                        // It would be nice if we could capture the TypeConverter here
                        return Publishers.map(byteBufferPublisher, value -> ConversionService.SHARED.convert(value, reactiveValueType).get());
                    } else {
                        throw new ConfigurationException("Cannot create the generated HTTP client's " +
                                "required return type, since no TypeConverter from ByteBuffer to " +
                                reactiveValueType + " is registered");
                    }
                }
            }
        }
    }

    private Object getValue(Argument argument,
                            MethodInvocationContext<?, ?> context,
                            Map<String, MutableArgumentValue<?>> parameters) {
        String argumentName = argument.getName();
        MutableArgumentValue<?> value = parameters.get(argumentName);

        Object definedValue = value.getValue();

        if (definedValue == null) {
            definedValue = argument.getAnnotationMetadata().stringValue(Bindable.class, "defaultValue").orElse(null);
        }

        if (definedValue == null && !argument.isNullable()) {
            throw new IllegalArgumentException(
                    String.format("Argument [%s] is null. Null values are not allowed to be passed to client methods (%s). Add a supported Nullable annotation type if that is the desired behaviour", argument.getName(), context.getExecutableMethod().toString())
            );
        }

        if (definedValue instanceof Optional) {
            return ((Optional) definedValue).orElse(null);
        } else {
            return definedValue;
        }
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
            if (StringUtils.isNotEmpty(value) && value.startsWith("/")) {
                return value + templateString;
            }
            return templateString;
        }
    }

    private String getClientId(AnnotationMetadata clientAnn) {
        return clientAnn.stringValue(Client.class).orElse(null);
    }

    private void addParametersToQuery(Map<String, Object> parameters, ClientRequestUriContext uriContext) {
        for (Map.Entry<String, Object> entry: parameters.entrySet()) {
            conversionService.convert(entry.getValue(), ConversionContext.STRING).ifPresent(v -> {
                conversionService.convert(entry.getKey(), ConversionContext.STRING).ifPresent(k -> {
                    uriContext.addQueryParameter(k, v);
                });
            });
        }
    }

    private String appendQuery(String uri, Map<String, List<String>> queryParams) {
        if (!queryParams.isEmpty()) {
            final UriBuilder builder = UriBuilder.of(uri);
            for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
                builder.queryParam(entry.getKey(), entry.getValue().toArray());
            }
            return builder.toString();
        }
        return uri;
    }
}
