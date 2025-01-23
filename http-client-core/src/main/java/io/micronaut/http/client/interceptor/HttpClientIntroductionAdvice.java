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
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
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
import io.micronaut.http.BasicHttpAttributes;
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
import io.micronaut.http.client.ClientAttributes;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.ReactiveClientResultTransformer;
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
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Introduction advice that implements the {@link Client} annotation.
 *
 * @author graemerocher
 * @since 1.0
 */
@InterceptorBean(Client.class)
@Internal
@BootstrapContextCompatible
public class HttpClientIntroductionAdvice implements MethodInterceptor<Object, Object> {

    private static final Logger LOG = LoggerFactory.getLogger(HttpClientIntroductionAdvice.class);

    /**
     * The default Accept-Types.
     */
    private static final MediaType[] DEFAULT_ACCEPT_TYPES = {MediaType.APPLICATION_JSON_TYPE};

    private final List<ReactiveClientResultTransformer> transformers;
    private final HttpClientBinderRegistry binderRegistry;
    private final JsonMediaTypeCodec jsonMediaTypeCodec;
    private final HttpClientRegistry<?> clientFactory;
    private final ConversionService conversionService;

    /**
     * Constructor for advice class to set up things like Headers, Cookies, Parameters for Clients.
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
            ConversionService conversionService) {
        this.clientFactory = clientFactory;
        this.jsonMediaTypeCodec = jsonMediaTypeCodec;
        this.transformers = transformers != null ? transformers : Collections.emptyList();
        this.binderRegistry = binderRegistry;
        this.conversionService = conversionService;
    }

    /**
     * Interceptor to apply headers, cookies, parameter and body arguments.
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
        if (httpMethodMapping.isPresent() && context.hasStereotype(HttpMethodMapping.class) && httpClient != null) {
            AnnotationValue<HttpMethodMapping> mapping = context.getAnnotation(HttpMethodMapping.class);
            String uri = mapping.getRequiredValue(String.class);
            if (StringUtils.isEmpty(uri)) {
                uri = "/" + context.getMethodName();
            }

            Class<? extends Annotation> annotationType = httpMethodMapping.get();
            HttpMethod httpMethod = HttpMethod.parse(annotationType.getSimpleName().toUpperCase(Locale.ENGLISH));
            String httpMethodName = context.stringValue(CustomHttpMethod.class, "method").orElse(httpMethod.name());

            InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);

            Argument<?> errorType = annotationMetadata.classValue(Client.class, "errorType")
                    .map(errorClass -> Argument.of(errorClass)).orElse(HttpClient.DEFAULT_ERROR_TYPE);

            ReturnType<?> returnType = context.getReturnType();

            try {
                Argument<?> valueType = interceptedMethod.returnTypeValue();
                Class<?> reactiveValueType = valueType.getType();
                return switch (interceptedMethod.resultType()) {
                    case PUBLISHER ->
                            handlePublisher(context, returnType, reactiveValueType, httpMethod, httpMethodName,
                                uri, interceptedMethod, annotationMetadata, httpClient, errorType, valueType, declaringType);
                    case COMPLETION_STAGE ->
                            handleCompletionStage(context, httpMethod, httpMethodName, uri, interceptedMethod,
                                annotationMetadata, httpClient, returnType, errorType, valueType, reactiveValueType, declaringType);
                    case SYNCHRONOUS ->
                            handleSynchronous(context, returnType, httpClient, httpMethod, httpMethodName, uri,
                                interceptedMethod, annotationMetadata, errorType, declaringType);
                };
            } catch (Exception e) {
                return interceptedMethod.handleException(e);
            }
        }
        // try other introduction advice
        return context.proceed();
    }

    @Nullable
    private Object handleSynchronous(MethodInvocationContext<Object, Object> context,
                                     ReturnType<?> returnType,
                                     HttpClient httpClient,
                                     HttpMethod httpMethod,
                                     String httpMethodName,
                                     String uriToBind,
                                     InterceptedMethod interceptedMethod,
                                     AnnotationMetadata annotationMetadata,
                                     Argument<?> errorType,
                                     Class<?> declaringType) {

        Class<?> javaReturnType = returnType.getType();
        BlockingHttpClient blockingHttpClient = httpClient.toBlocking();
        RequestBinderResult binderResult = bindRequest(context, httpMethod, httpMethodName, uriToBind, interceptedMethod, annotationMetadata);
        String clientName = declaringType.getName();

        if (binderResult.isError()) {
            return binderResult.errorResult;
        }

        MutableHttpRequest<?> request = binderResult.request;

        if (void.class == javaReturnType || httpMethod == HttpMethod.HEAD) {
            request.getHeaders().remove(HttpHeaders.ACCEPT);
        }

        if (HttpResponse.class.isAssignableFrom(javaReturnType)) {
            return handleBlockingCall(
                clientName, javaReturnType, () ->
                    blockingHttpClient.exchange(request,
                    returnType.asArgument().getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT),
                    errorType
                ));
        } else if (void.class == javaReturnType) {
            return handleBlockingCall(clientName, javaReturnType, () -> blockingHttpClient.exchange(request, null, errorType));
        } else {
            return handleBlockingCall(clientName, javaReturnType,
                () -> blockingHttpClient.retrieve(request, returnType.asArgument(), errorType));
        }
    }

    private Object handleCompletionStage(MethodInvocationContext<Object, Object> context,
                                         HttpMethod httpMethod,
                                         String httpMethodName,
                                         String uriToBind,
                                         InterceptedMethod interceptedMethod,
                                         AnnotationMetadata annotationMetadata,
                                         HttpClient httpClient,
                                         ReturnType<?> returnType,
                                         Argument<?> errorType,
                                         Argument<?> valueType,
                                         Class<?> reactiveValueType,
                                         Class<?> declaringType) {

        Publisher<RequestBinderResult> csRequestPublisher = Mono.fromCallable(() ->
            bindRequest(context, httpMethod, httpMethodName, uriToBind, interceptedMethod, annotationMetadata));
        Publisher<?> csPublisher = httpClientResponsePublisher(httpClient, csRequestPublisher, returnType, errorType, valueType);
        CompletableFuture<Object> future = new CompletableFuture<>();
        csPublisher.subscribe(new CompletionAwareSubscriber<Object>() {
            Object message;
            Subscription subscription;

            @Override
            protected void doOnSubscribe(Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            protected void doOnNext(Object message) {
                if (Void.class != reactiveValueType) {
                    this.message = message;
                }
                // we only want the first item
                subscription.cancel();
                doOnComplete();
            }

            @Override
            protected void doOnError(Throwable t) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Client [{}] received HTTP error response: {}", declaringType.getName(), t.getMessage(), t);
                }

                if (t instanceof HttpClientResponseException e) {
                    if (e.code() == HttpStatus.NOT_FOUND.getCode()) {
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

                future.completeExceptionally(t);
            }

            @Override
            protected void doOnComplete() {
                // can be called twice
                future.complete(message);
            }
        });
        return interceptedMethod.handleResult(future);
    }

    private Object handlePublisher(MethodInvocationContext<Object, Object> context,
                                   ReturnType<?> returnType,
                                   Class<?> reactiveValueType,
                                   HttpMethod httpMethod,
                                   String httpMethodName,
                                   String uriToBind,
                                   InterceptedMethod interceptedMethod,
                                   AnnotationMetadata annotationMetadata,
                                   HttpClient httpClient,
                                   Argument<?> errorType,
                                   Argument<?> valueType,
                                   Class<?> declaringType) {
        boolean isSingle = returnType.isSingleResult() ||
                returnType.isCompletable() ||
                HttpResponse.class.isAssignableFrom(reactiveValueType) ||
                HttpStatus.class == reactiveValueType;

        Publisher<RequestBinderResult> requestPublisher = Mono.fromCallable(() ->
            bindRequest(context, httpMethod, httpMethodName, uriToBind, interceptedMethod, annotationMetadata));
        Publisher<?> publisher;
        if (!isSingle && httpClient instanceof StreamingHttpClient client) {
            publisher = httpClientResponseStreamingPublisher(client, context, requestPublisher, errorType, valueType);
        } else {
            publisher = httpClientResponsePublisher(httpClient, requestPublisher, returnType, errorType, valueType);
        }

        if (LOG.isDebugEnabled()) {
            publisher = Flux.from(publisher).doOnError(t ->
                LOG.debug("Client [{}] received HTTP error response: {}", declaringType.getName(), t.getMessage(), t)
            );
        }

        Object finalPublisher = interceptedMethod.handleResult(publisher);
        for (ReactiveClientResultTransformer transformer : transformers) {
            finalPublisher = transformer.transform(finalPublisher);
        }
        return finalPublisher;
    }

    @NonNull
    private RequestBinderResult bindRequest(MethodInvocationContext<Object, Object> context,
                                            HttpMethod httpMethod,
                                            String httpMethodName,
                                            String uri,
                                            InterceptedMethod interceptedMethod,
                                            AnnotationMetadata annotationMetadata) {
        MutableHttpRequest<?> request = HttpRequest.create(httpMethod, "", httpMethodName);

        UriMatchTemplate uriTemplate = UriMatchTemplate.of("");
        if (!(uri.length() == 1 && uri.charAt(0) == '/')) {
            uriTemplate = uriTemplate.nest(uri);
        }

        Map<String, Object> pathParams = new HashMap<>();
        Map<String, List<String>> queryParams = new LinkedHashMap<>();
        ClientRequestUriContext uriContext = new ClientRequestUriContext(uriTemplate, pathParams, queryParams);
        List<Argument<?>> bodyArguments = new ArrayList<>();

        List<String> uriVariables = uriTemplate.getVariableNames();
        Map<String, MutableArgumentValue<?>> parameters = context.getParameters();

        ClientArgumentRequestBinder<Object> defaultBinder = buildDefaultBinder(pathParams, bodyArguments);

        // Apply all the method binders
        List<Class<? extends Annotation>> methodBinderTypes = context.getAnnotationTypesByStereotype(Bindable.class);
        // @Version is not a bindable, so it needs to looked for separately
        methodBinderTypes.addAll(context.getAnnotationTypesByStereotype(Version.class));
        if (!CollectionUtils.isEmpty(methodBinderTypes)) {
            for (Class<? extends Annotation> binderType : methodBinderTypes) {
                binderRegistry.findAnnotatedBinder(binderType).ifPresent(b -> b.bind(context, uriContext, request));
            }
        }

        // Apply all the argument binders
        Optional<Object> bindingErrorResult = bindArguments(context, parameters, defaultBinder, uriContext, request, interceptedMethod);

        if (bindingErrorResult.isPresent()) {
            return RequestBinderResult.withErrorResult(bindingErrorResult.get());
        }

        Object body = bindRequestBody(request, bodyArguments, parameters);

        bindPathParams(uriVariables, pathParams, body);

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

        final MediaType[] acceptTypes;
        Collection<MediaType> accept = request.accept();
        var definitionType = annotationMetadata.enumValue(Client.class, "definitionType", Client.DefinitionType.class)
            .orElse(Client.DefinitionType.CLIENT);
        if (accept.isEmpty()) {
            String[] consumesMediaType = context.stringValues(definitionType.isClient() ? Consumes.class : Produces.class);
            if (ArrayUtils.isEmpty(consumesMediaType)) {
                acceptTypes = DEFAULT_ACCEPT_TYPES;
            } else {
                acceptTypes = MediaType.of(consumesMediaType);
            }
            request.accept(acceptTypes);
        }

        if (body != null && request.getContentType().isEmpty()) {
            MediaType[] contentTypes = MediaType.of(context.stringValues(definitionType.isClient() ? Produces.class : Consumes.class));
            if (ArrayUtils.isEmpty(contentTypes)) {
                contentTypes = DEFAULT_ACCEPT_TYPES;
            }
            if (ArrayUtils.isNotEmpty(contentTypes)) {
                request.contentType(contentTypes[0]);
            }
        }

        ClientAttributes.setInvocationContext(request, context);
        // Set the URI template used to make the request for tracing purposes
        BasicHttpAttributes.setUriTemplate(request, resolveTemplate(annotationMetadata, uriTemplate.toString()));

        return RequestBinderResult.withRequest(request);
    }

    private void bindPathParams(List<String> uriVariables, Map<String, Object> pathParams, Object body) {
        boolean variableSatisfied = uriVariables.isEmpty() || pathParams.keySet().containsAll(uriVariables);
        if (body != null && !variableSatisfied) {
            if (body instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String k = entry.getKey().toString();
                    Object v = entry.getValue();
                    if (v != null) {
                        pathParams.putIfAbsent(k, v);
                    }
                }
            } else if (!Publishers.isConvertibleToPublisher(body)) {
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
    }

    @Nullable
    private Object bindRequestBody(MutableHttpRequest<?> request, List<Argument<?>> bodyArguments, Map<String, MutableArgumentValue<?>> parameters) {
        Object body = request.getBody().orElse(null);
        if (body == null && !bodyArguments.isEmpty()) {
            Map<String, Object> bodyMap = new LinkedHashMap<>();

            for (Argument<?> bodyArgument : bodyArguments) {
                String argumentName = bodyArgument.getName();
                MutableArgumentValue<?> value = parameters.get(argumentName);
                if (bodyArgument.getAnnotationMetadata().hasStereotype(Format.class)) {
                    conversionService.convert(value.getValue(), ConversionContext.STRING.with(bodyArgument.getAnnotationMetadata()))
                        .ifPresent(v -> bodyMap.put(argumentName, v));
                } else {
                    bodyMap.put(argumentName, value.getValue());
                }
            }
            body = bodyMap;
            request.body(body);
        }
        return body;
    }

    @NonNull
    private ClientArgumentRequestBinder<Object> buildDefaultBinder(Map<String, Object> pathParams, List<Argument<?>> bodyArguments) {
        return (ctx, uriCtx, value, req) -> {
            Argument<?> argument = ctx.getArgument();
            if (uriCtx.getUriTemplate().getVariableNames().contains(argument.getName())) {
                String name = argument.getAnnotationMetadata().stringValue(Bindable.class)
                    .orElse(argument.getName());
                // Convert and put as path param
                if (argument.getAnnotationMetadata().hasStereotype(Format.class)) {
                    conversionService.convert(value,
                            ConversionContext.STRING.with(argument.getAnnotationMetadata()))
                        .ifPresent(v -> pathParams.put(name, v));
                } else {
                    pathParams.put(name, value);
                }
            } else {
                bodyArguments.add(ctx.getArgument());
            }
        };
    }

    @NonNull
    private Optional<Object> bindArguments(MethodInvocationContext<Object, Object> context,
                                           Map<String, MutableArgumentValue<?>> parameters,
                                           ClientArgumentRequestBinder<Object> defaultBinder,
                                           ClientRequestUriContext uriContext,
                                           MutableHttpRequest<?> request,
                                           InterceptedMethod interceptedMethod) {
        Optional<Object> bindingErrorResult = Optional.empty();
        Argument<?>[] arguments = context.getArguments();
        for (Argument<?> argument : arguments) {
            Object definedValue = getValue(argument, context, parameters);

            if (definedValue != null) {
                final ClientArgumentRequestBinder<Object> binder = (ClientArgumentRequestBinder<Object>) binderRegistry
                    .findArgumentBinder((Argument<Object>) argument)
                    .orElse(defaultBinder);
                ArgumentConversionContext conversionContext = ConversionContext.of(argument);
                binder.bind(conversionContext, uriContext, definedValue, request);
                if (conversionContext.hasErrors()) {
                    return conversionContext.getLastError().map(e -> interceptedMethod.handleException(new ConversionErrorException(argument, e)));
                }
            }
        }
        return bindingErrorResult;
    }

    private Publisher<?> httpClientResponsePublisher(HttpClient httpClient,
                                                     Publisher<RequestBinderResult> requestPublisher,
                                                     ReturnType<?> returnType,
                                                     Argument<?> errorType,
                                                     Argument<?> reactiveValueArgument) {
        Flux<RequestBinderResult> requestFlux = Flux.from(requestPublisher);
        return requestFlux.filter(result -> !result.isError).map(RequestBinderResult::request).flatMap(request -> {
            Class<?> argumentType = reactiveValueArgument.getType();
            if (Void.class == argumentType || returnType.isVoid()) {
                request.getHeaders().remove(HttpHeaders.ACCEPT);
                return httpClient.retrieve(request, Argument.VOID, errorType);
            } else {
                if (HttpResponse.class.isAssignableFrom(argumentType)) {
                    return httpClient.exchange(request, reactiveValueArgument, errorType);
                }
                return httpClient.retrieve(request, reactiveValueArgument, errorType);
            }
        }).switchIfEmpty(requestFlux.mapNotNull(RequestBinderResult::errorResult));
    }

    private Publisher<?> httpClientResponseStreamingPublisher(StreamingHttpClient streamingHttpClient,
                                                           MethodInvocationContext<Object, Object> context,
                                                           Publisher<RequestBinderResult> requestPublisher,
                                                           Argument<?> errorType,
                                                           Argument<?> reactiveValueArgument) {
        Flux<RequestBinderResult> requestFlux = Flux.from(requestPublisher);
        return requestFlux.filter(result -> !result.isError()).map(RequestBinderResult::request).flatMap(request -> {
            Class<?> reactiveValueType = reactiveValueArgument.getType();
            if (Void.class == reactiveValueType) {
                request.getHeaders().remove(HttpHeaders.ACCEPT);
            }

            Collection<MediaType> acceptTypes = request.accept();

            if (streamingHttpClient instanceof SseClient sseClient && acceptTypes.contains(MediaType.TEXT_EVENT_STREAM_TYPE)) {
                if (reactiveValueArgument.getType() == Event.class) {
                    return sseClient.eventStream(
                        request, reactiveValueArgument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT), errorType
                    );
                }
                return Publishers.map(sseClient.eventStream(request, reactiveValueArgument, errorType), Event::getData);
            } else {
                if (isJsonParsedMediaType(acceptTypes)) {
                    return streamingHttpClient.jsonStream(request, reactiveValueArgument, errorType);
                } else {
                    Publisher<ByteBuffer<?>> byteBufferPublisher = streamingHttpClient.dataStream(request, errorType);
                    if (reactiveValueType == ByteBuffer.class) {
                        return byteBufferPublisher;
                    } else {
                        if (conversionService.canConvert(ByteBuffer.class, reactiveValueType)) {
                            // It would be nice if we could capture the TypeConverter here
                            return Publishers.map(byteBufferPublisher, value -> conversionService.convert(value, reactiveValueType).get());
                        } else {
                            return Flux.error(new ConfigurationException("Cannot create the generated HTTP client's " +
                                "required return type, since no TypeConverter from ByteBuffer to " +
                                reactiveValueType + " is registered"));
                        }
                    }
                }
            }
        }).switchIfEmpty(requestFlux.mapNotNull(RequestBinderResult::errorResult));
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
            ("Argument [%s] is null. Null values are not allowed to be passed to client methods (%s). Add a supported Nullable " +
                "annotation type if that is the desired behaviour").formatted(argument.getName(), context.getExecutableMethod().toString())
            );
        }

        if (definedValue instanceof Optional optional) {
            return optional.orElse(null);
        } else {
            return definedValue;
        }
    }

    private Object handleBlockingCall(String clientName, Class returnType, Supplier<Object> supplier) {
        try {
            if (void.class == returnType) {
                supplier.get();
                return null;
            } else {
                return supplier.get();
            }
        } catch (RuntimeException t) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Client [{}] received HTTP error response: {}", clientName, t.getMessage(), t);
            }

            if (t instanceof HttpClientResponseException exception && exception.code() == HttpStatus.NOT_FOUND.getCode()) {
                if (returnType == Optional.class) {
                    return Optional.empty();
                } else if (HttpResponse.class.isAssignableFrom(returnType)) {
                    return exception.getResponse();
                }
                return null;
            } else {
                throw t;
            }
        }
    }

    private boolean isJsonParsedMediaType(Collection<MediaType> acceptTypes) {
        return acceptTypes.stream().anyMatch(mediaType ->
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

    private record RequestBinderResult(
        @Nullable MutableHttpRequest<?> request,
        @Nullable Object errorResult,
        boolean isError
    ) {

        static RequestBinderResult withRequest(@NonNull MutableHttpRequest<?> request) {
            Objects.requireNonNull(request, "Bound HTTP request must not be null");
            return new RequestBinderResult(request, null, false);
        }

        static RequestBinderResult withErrorResult(@Nullable Object errorResult) {
            return new RequestBinderResult(null, errorResult, true);
        }
    }
}
