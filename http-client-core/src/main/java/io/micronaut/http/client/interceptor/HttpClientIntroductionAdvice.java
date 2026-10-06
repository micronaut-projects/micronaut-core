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
import io.micronaut.aop.kotlin.KotlinInterceptedMethod;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.beans.BeanMap;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ArrayUtils;
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
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.client.AsyncHttpClient;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.ClientAttributes;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientRegistry;
import io.micronaut.http.client.ReactiveClientResultTransformer;
import io.micronaut.http.client.AsyncStreamingHttpClient;
import io.micronaut.http.client.ElementsResponse;
import io.micronaut.http.client.MappedBodyElements;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.bind.ClientArgumentRequestBinder;
import io.micronaut.http.client.bind.ClientRequestUriContext;
import io.micronaut.http.client.bind.HttpClientBinderRegistry;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.sse.AsyncSseClient;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.sse.Event;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.json.codec.JsonMediaTypeCodec;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
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
    private static final String HTTP_ERROR_RESPONSE_LOG_MESSAGE = "Client [{}] received HTTP error response: {}";

    /**
     * The default Accept-Types.
     */
    private static final MediaType[] DEFAULT_ACCEPT_TYPES = {MediaType.APPLICATION_JSON_TYPE};
    /**
     * Upper bound for {@link #methodCache}. Method metadata is bounded by the number of declarative
     * client methods; the cap only guards against a caller that passes a new metadata instance on
     * every call, in which case the method data is no longer cached.
     */
    private static final int METHOD_CACHE_MAX_SIZE = 2048;

    private final List<ReactiveClientResultTransformer> transformers;
    private final HttpClientBinderRegistry binderRegistry;
    private final JsonMediaTypeCodec jsonMediaTypeCodec;
    private final HttpClientRegistry<?> clientFactory;
    private final ConversionService conversionService;
    /**
     * Cache of the per-method client call data computed for an annotation metadata instance, keyed
     * by identity. A declarative client passes the same (generated) metadata instance on every call
     * of a method, so this avoids repeating the annotation lookups and parsing the URI template on
     * every call. The map is copy-on-write and never mutated once published: reads are lock-free and
     * misses publish a new copy under {@link #methodCacheLock}.
     */
    @SuppressWarnings("java:S3077") // the published map is never modified, volatile only publishes it
    private volatile IdentityHashMap<AnnotationMetadata, ClientMethod> methodCache = new IdentityHashMap<>();
    private final Object methodCacheLock = new Object();

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
        final AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();
        ClientMethod clientMethod = methodCache.get(annotationMetadata);
        if (clientMethod == null && !context.hasStereotype(Client.class)) {
            throw new IllegalStateException("Client advice called from type that is not annotated with @Client: " + context);
        }

        Class<?> declaringType = context.getDeclaringType();
        if (Closeable.class == declaringType || AutoCloseable.class == declaringType) {
            clientFactory.disposeClient(annotationMetadata);
            return null;
        }

        HttpClient httpClient = clientFactory.getClient(annotationMetadata);
        clientMethod = resolveClientMethod(context, annotationMetadata, clientMethod);
        if (clientMethod.mapped()) {
            HttpMethod httpMethod = clientMethod.httpMethod();
            String httpMethodName = clientMethod.httpMethodName();

            InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);

            Argument<?> errorType = clientMethod.errorType();

            ReturnType<?> returnType = context.getReturnType();

            try {
                Argument<?> valueType = interceptedMethod.returnTypeValue();
                Class<?> reactiveValueType = valueType.getType();
                // When io.micrometer:context-propagation enables Reactor automatic context
                // propagation, ThreadLocals may be cleared around coroutine resumes even though
                // the PropagatedContext is still present in the Kotlin coroutine context.
                // Re-apply it for the synchronous client setup so filters capture the right context.
                // Scope open is only attempted for Kotlin suspend clients (no Supplier alloc).
                PropagatedContext.Scope kotlinScope = null;
                try {
                    if (interceptedMethod instanceof KotlinInterceptedMethod kotlinInterceptedMethod) {
                        kotlinScope = KotlinClientPropagatedContext.maybePropagate(kotlinInterceptedMethod);
                    }
                    return dispatchClientCall(
                        context, returnType, reactiveValueType, httpMethod, httpMethodName, clientMethod,
                        interceptedMethod, annotationMetadata, httpClient, errorType, valueType, declaringType);
                } finally {
                    if (kotlinScope != null) {
                        kotlinScope.close();
                    }
                }
            } catch (Exception e) {
                return interceptedMethod.handleException(e);
            }
        }
        // try other introduction advice
        return context.proceed();
    }

    /**
     * Returns the client call data of the invoked method, computing and caching it on a miss.
     *
     * @param context            The invocation context
     * @param annotationMetadata The annotation metadata of the method, the cache key
     * @param cached             The cached data or {@code null}
     * @return The client call data
     */
    private ClientMethod resolveClientMethod(MethodInvocationContext<Object, Object> context,
                                             AnnotationMetadata annotationMetadata,
                                             @Nullable ClientMethod cached) {
        MethodValues values = null;
        if (cached != null) {
            if (!cached.revalidate()) {
                return cached;
            }
            // Values with property placeholders are resolved by the environment on access and may
            // change on a refresh, so they are read again and the cached data is reused only when
            // they are unchanged.
            values = MethodValues.read(context);
            if (values.equals(cached.values())) {
                return cached;
            }
        }
        ClientMethod clientMethod = ClientMethod.of(context, annotationMetadata, values);
        // Metadata with evaluated expressions (EvaluatedAnnotationMetadata) is re-created for every
        // invocation and its values may depend on the call arguments, so it is never cached.
        if (!annotationMetadata.hasEvaluatedExpressions()) {
            synchronized (methodCacheLock) {
                IdentityHashMap<AnnotationMetadata, ClientMethod> current = methodCache;
                if (current.containsKey(annotationMetadata) || current.size() < METHOD_CACHE_MAX_SIZE) {
                    IdentityHashMap<AnnotationMetadata, ClientMethod> copy = new IdentityHashMap<>(current);
                    copy.put(annotationMetadata, clientMethod);
                    methodCache = copy;
                }
            }
        }
        return clientMethod;
    }

    @Nullable
    private Object dispatchClientCall(MethodInvocationContext<Object, Object> context,
                                      ReturnType<?> returnType,
                                      Class<?> reactiveValueType,
                                      HttpMethod httpMethod,
                                      String httpMethodName,
                                      ClientMethod clientMethod,
                                      InterceptedMethod interceptedMethod,
                                      AnnotationMetadata annotationMetadata,
                                      HttpClient httpClient,
                                      Argument<?> errorType,
                                      Argument<?> valueType,
                                      Class<?> declaringType) {
        return switch (interceptedMethod.resultType()) {
            case PUBLISHER ->
                handlePublisher(context, returnType, reactiveValueType, httpMethod, httpMethodName,
                    clientMethod, interceptedMethod, annotationMetadata, httpClient, errorType, valueType, declaringType);
            case COMPLETION_STAGE ->
                handleCompletionStage(context, httpMethod, httpMethodName, clientMethod, interceptedMethod,
                    annotationMetadata, httpClient, returnType, errorType, valueType, reactiveValueType, declaringType);
            case SYNCHRONOUS ->
                handleSynchronous(context, returnType, httpClient, httpMethod, httpMethodName, clientMethod,
                    interceptedMethod, annotationMetadata, errorType, declaringType);
        };
    }

    @Nullable
    private Object handleSynchronous(MethodInvocationContext<Object, Object> context,
                                     ReturnType<?> returnType,
                                     HttpClient httpClient,
                                     HttpMethod httpMethod,
                                     String httpMethodName,
                                     ClientMethod clientMethod,
                                     InterceptedMethod interceptedMethod,
                                     AnnotationMetadata annotationMetadata,
                                     Argument<?> errorType,
                                     Class<?> declaringType) {

        Class<?> javaReturnType = returnType.getType();
        BlockingHttpClient blockingHttpClient = httpClient.toBlocking();
        RequestBinderResult binderResult = bindRequest(context, httpMethod, httpMethodName, clientMethod, interceptedMethod);
        String clientName = declaringType.getName();

        if (binderResult.isError()) {
            return binderResult.errorResult;
        }

        MutableHttpRequest<?> request = Objects.requireNonNull(binderResult.request);

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
            return handleBlockingCall(clientName, javaReturnType, () -> blockingHttpClient.exchange(request, Argument.OBJECT_ARGUMENT, errorType));
        } else {
            return handleBlockingCall(clientName, javaReturnType,
                () -> blockingHttpClient.retrieve(request, returnType.asArgument(), errorType));
        }
    }

    @Nullable
    private Object handleCompletionStage(MethodInvocationContext<Object, Object> context,
                                         HttpMethod httpMethod,
                                         String httpMethodName,
                                         ClientMethod clientMethod,
                                         InterceptedMethod interceptedMethod,
                                         AnnotationMetadata annotationMetadata,
                                         HttpClient httpClient,
                                         ReturnType<?> returnType,
                                         Argument<?> errorType,
                                         Argument<?> valueType,
                                         Class<?> reactiveValueType,
                                         Class<?> declaringType) {
        try {
            RequestBinderResult binderResult = bindRequest(context, httpMethod, httpMethodName, clientMethod, interceptedMethod);
            CompletableFuture<@Nullable Object> future = new CompletableFuture<>();
            if (binderResult.isError()) {
                future.complete(binderResult.errorResult());
            } else {
                MutableHttpRequest<?> request = Objects.requireNonNull(binderResult.request());
                CompletionStage<?> responseStage = isBodyElements(valueType)
                    ? httpClientElementsStage(httpClient, request, valueType, errorType)
                    : httpClientResponseStage(httpClient.toAsync(), request, returnType, errorType, valueType);
                responseStage.whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        Throwable cause = (throwable instanceof CompletionException completionException && completionException.getCause() != null)
                            ? completionException.getCause()
                            : throwable;
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(HTTP_ERROR_RESPONSE_LOG_MESSAGE, declaringType.getName(), cause.getMessage(), cause);
                        }
                        if (cause instanceof HttpClientResponseException e && e.code() == HttpStatus.NOT_FOUND.getCode()) {
                            if (reactiveValueType == Optional.class) {
                                future.complete(Optional.empty());
                                return;
                            } else if (HttpResponse.class.isAssignableFrom(reactiveValueType)) {
                                future.complete(e.getResponse());
                                return;
                            } else {
                                future.complete(null);
                                return;
                            }
                        }
                        future.completeExceptionally(cause);
                    } else {
                        future.complete(result);
                    }
                });
            }
            return interceptedMethod.handleResult(future);
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    @Nullable
    private Object handlePublisher(MethodInvocationContext<Object, Object> context,
                                   ReturnType<?> returnType,
                                   Class<?> reactiveValueType,
                                   HttpMethod httpMethod,
                                   String httpMethodName,
                                   ClientMethod clientMethod,
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
            bindRequest(context, httpMethod, httpMethodName, clientMethod, interceptedMethod));
        Publisher<?> publisher;
        if (!isSingle && httpClient instanceof StreamingHttpClient client) {
            publisher = httpClientResponseStreamingPublisher(client, context, requestPublisher, errorType, valueType);
        } else {
            publisher = httpClientResponsePublisher(httpClient, requestPublisher, returnType, errorType, valueType);
        }

        if (LOG.isDebugEnabled()) {
            publisher = Flux.from(publisher).doOnError(t ->
                LOG.debug(HTTP_ERROR_RESPONSE_LOG_MESSAGE, declaringType.getName(), t.getMessage(), t)
            );
        }

        Object finalPublisher = interceptedMethod.handleResult(publisher);
        if (finalPublisher != null) {
            for (ReactiveClientResultTransformer transformer : transformers) {
                finalPublisher = transformer.transform(finalPublisher);
            }
        }
        return finalPublisher;
    }

    private RequestBinderResult bindRequest(MethodInvocationContext<Object, Object> context,
                                            HttpMethod httpMethod,
                                            String httpMethodName,
                                            ClientMethod clientMethod,
                                            InterceptedMethod interceptedMethod) {
        MutableHttpRequest<?> request = HttpRequest.create(httpMethod, "", httpMethodName);

        UriBinding uriBinding = clientMethod.uriBinding();
        UriMatchTemplate uriTemplate = uriBinding.uriTemplate();

        Map<String, Object> pathParams = new HashMap<>();
        Map<String, List<String>> queryParams = new LinkedHashMap<>();
        ClientRequestUriContext uriContext = new ClientRequestUriContext(uriTemplate, pathParams, queryParams);
        List<Argument<?>> bodyArguments = new ArrayList<>();

        List<String> uriVariables = uriBinding.variableNames();
        Map<String, MutableArgumentValue<?>> parameters = context.getParameters();

        ClientArgumentRequestBinder<Object> defaultBinder = buildDefaultBinder(pathParams, bodyArguments, uriVariables);

        // Apply all the method binders
        for (Class<? extends Annotation> binderType : clientMethod.methodBinderTypes()) {
            binderRegistry.findAnnotatedBinder(binderType).ifPresent(b -> b.bind(context, uriContext, request));
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

        String uri = uriTemplate.expand(pathParams);
        // Remove all the pathParams that have already been used.
        // Other path parameters are added to query
        uriVariables.forEach(pathParams::remove);
        addParametersToQuery(pathParams, uriContext);

        // The original query can be added by getting it from the request.getUri() and appending
        request.uri(URI.create(appendQuery(uri, uriContext.getQueryParameters())));

        Collection<MediaType> accept = request.accept();
        if (accept.isEmpty()) {
            request.accept(clientMethod.acceptTypes());
        }

        if (body != null && request.getContentType().isEmpty()) {
            request.contentType(clientMethod.contentType());
        }

        ClientAttributes.setInvocationContext(request, context);
        // Set the URI template used to make the request for tracing purposes
        BasicHttpAttributes.setUriTemplate(request, uriBinding.tracingTemplate());

        return RequestBinderResult.withRequest(request);
    }

    private void bindPathParams(List<String> uriVariables, Map<String, Object> pathParams, @Nullable Object body) {
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
            Map<String, @Nullable Object> bodyMap = new LinkedHashMap<>();

            for (Argument<?> bodyArgument : bodyArguments) {
                String argumentName = bodyArgument.getName();
                MutableArgumentValue<?> value = Objects.requireNonNull(parameters.get(argumentName));
                Object argumentValue = value.getValue();
                if (bodyArgument.getAnnotationMetadata().hasStereotype(Format.class)) {
                    conversionService.convert(argumentValue, ConversionContext.STRING.with(bodyArgument.getAnnotationMetadata()))
                        .ifPresent(v -> bodyMap.put(argumentName, v));
                } else {
                    bodyMap.put(argumentName, argumentValue);
                }
            }
            body = bodyMap;
            request.body(body);
        }
        return body;
    }

    private ClientArgumentRequestBinder<Object> buildDefaultBinder(Map<String, Object> pathParams,
                                                                   List<Argument<?>> bodyArguments,
                                                                   List<String> uriVariables) {
        return (ctx, uriCtx, value, req) -> {
            Argument<?> argument = ctx.getArgument();
            if (uriVariables.contains(argument.getName())) {
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
        return Flux.from(requestPublisher).flatMap(result -> {
            if (result.isError()) {
                return errorResultPublisher(result);
            }
            return httpClientResponse(httpClient, Objects.requireNonNull(result.request()), returnType, errorType, reactiveValueArgument);
        });
    }

    private Publisher<?> httpClientResponse(HttpClient httpClient,
                                            MutableHttpRequest<?> request,
                                            ReturnType<?> returnType,
                                            Argument<?> errorType,
                                            Argument<?> reactiveValueArgument) {
        Class<?> argumentType = reactiveValueArgument.getType();
        if (Void.class == argumentType || returnType.isVoid()) {
            request.getHeaders().remove(HttpHeaders.ACCEPT);
            return httpClient.retrieve(request, Argument.VOID, errorType);
        }
        if (HttpResponse.class.isAssignableFrom(argumentType)) {
            return httpClient.exchange(request, reactiveValueArgument, errorType);
        }
        return httpClient.retrieve(request, reactiveValueArgument, errorType);
    }

    /**
     * The publisher emitted when binding the request failed. The error result has already been
     * computed by the binding step, so it is emitted as-is and the binding is never repeated.
     * A reactive error result is flattened so the caller observes the failure of its publisher.
     */
    private static Publisher<?> errorResultPublisher(RequestBinderResult result) {
        Object errorResult = result.errorResult();
        if (errorResult instanceof Publisher<?> errorPublisher) {
            return errorPublisher;
        }
        return Mono.justOrEmpty(errorResult);
    }

    private Publisher<?> httpClientResponseStreamingPublisher(StreamingHttpClient streamingHttpClient,
                                                           MethodInvocationContext<Object, Object> context,
                                                           Publisher<RequestBinderResult> requestPublisher,
                                                           Argument<?> errorType,
                                                           Argument<?> reactiveValueArgument) {
        return Flux.from(requestPublisher).flatMap(result -> {
            if (result.isError()) {
                return errorResultPublisher(result);
            }
            return httpClientStreamingResponse(streamingHttpClient, Objects.requireNonNull(result.request()), errorType, reactiveValueArgument);
        });
    }

    private Publisher<?> httpClientStreamingResponse(StreamingHttpClient streamingHttpClient,
                                                     MutableHttpRequest<?> request,
                                                     Argument<?> errorType,
                                                     Argument<?> reactiveValueArgument) {
        Class<?> reactiveValueType = reactiveValueArgument.getType();
        if (Void.class == reactiveValueType) {
            request.getHeaders().remove(HttpHeaders.ACCEPT);
        }

        Collection<MediaType> acceptTypes = request.accept();

        if (streamingHttpClient instanceof SseClient sseClient && acceptTypes.contains(MediaType.TEXT_EVENT_STREAM_TYPE)) {
            if (reactiveValueType == Event.class) {
                return sseClient.eventStream(
                    request, reactiveValueArgument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT), errorType
                );
            }
            return Publishers.map(sseClient.eventStream(request, reactiveValueArgument, errorType), Event::getData);
        }
        if (isJsonParsedMediaType(acceptTypes)) {
            return streamingHttpClient.jsonStream(request, reactiveValueArgument, errorType);
        }
        Publisher<ByteBuffer<?>> byteBufferPublisher = streamingHttpClient.dataStream(request, errorType);
        if (reactiveValueType == ByteBuffer.class) {
            return byteBufferPublisher;
        }
        if (conversionService.canConvert(ByteBuffer.class, reactiveValueType)) {
            // It would be nice if we could capture the TypeConverter here
            return Publishers.map(byteBufferPublisher, value -> conversionService.convert(value, reactiveValueType).get());
        }
        return Flux.error(new ConfigurationException("Cannot create the generated HTTP client's " +
            "required return type, since no TypeConverter from ByteBuffer to " +
            reactiveValueType + " is registered"));
    }

    /**
     * Whether the value of a stage is the elements of the response body, or the response with
     * them: the asynchronous counterpart of a streaming publisher.
     *
     * @param valueArgument The value of the stage
     * @return Whether the value is streamed as {@link BodyElements}
     */
    private static boolean isBodyElements(Argument<?> valueArgument) {
        Class<?> type = valueArgument.getType();
        if (HttpResponse.class.isAssignableFrom(type)) {
            return valueArgument.getFirstTypeVariable().map(body -> BodyElements.class == body.getType()).orElse(false);
        }
        return BodyElements.class == type;
    }

    /**
     * The stage of a method that returns {@code CompletionStage<BodyElements<T>>} or
     * {@code CompletionStage<HttpResponse<BodyElements<T>>>}, read like a streaming publisher:
     * the events of an event stream, the elements of JSON, or the pieces of the body.
     *
     * @param httpClient    The client
     * @param request       The request
     * @param valueArgument The value of the stage
     * @param errorType     The error type
     * @return The stage of the elements, or of the response with them
     */
    @SuppressWarnings("unchecked")
    private CompletionStage<?> httpClientElementsStage(HttpClient httpClient,
                                                       MutableHttpRequest<?> request,
                                                       Argument<?> valueArgument,
                                                       Argument<?> errorType) {
        boolean exchange = HttpResponse.class.isAssignableFrom(valueArgument.getType());
        Argument<?> elementsArgument = exchange ? valueArgument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT) : valueArgument;
        Argument<?> elementArgument = elementsArgument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        Collection<MediaType> acceptTypes = request.accept();

        if (httpClient instanceof SseClient sseClient && acceptTypes.contains(MediaType.TEXT_EVENT_STREAM_TYPE)) {
            AsyncSseClient asyncSseClient = sseClient.toAsyncSse();
            boolean events = elementArgument.getType() == Event.class;
            Argument<Object> dataArgument = (Argument<Object>) (events ? elementArgument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT) : elementArgument);
            Function<BodyElements<Event<Object>>, BodyElements<?>> elements = events ? e -> e : e -> MappedBodyElements.map(e, Event::getData);
            if (exchange) {
                return asyncSseClient.exchangeEventStream(request, dataArgument, errorType)
                    .thenApply(response -> ElementsResponse.of(response, elements.apply(Objects.requireNonNull(response.body()))));
            }
            return asyncSseClient.eventStream(request, dataArgument, errorType).thenApply(elements);
        }
        if (!(httpClient instanceof StreamingHttpClient streamingHttpClient)) {
            return CompletableFuture.failedStage(new ConfigurationException("The HTTP client " + httpClient.getClass().getName()
                + " does not stream response bodies, which the return type BodyElements needs"));
        }
        AsyncStreamingHttpClient asyncStreamingHttpClient = streamingHttpClient.toAsync();
        if (isJsonParsedMediaType(acceptTypes)) {
            if (exchange) {
                return CompletableFuture.failedStage(new ConfigurationException("The response of a JSON stream is not available:"
                    + " declare the return type CompletionStage<BodyElements<T>> instead of CompletionStage<HttpResponse<BodyElements<T>>>"));
            }
            return asyncStreamingHttpClient.jsonStream(request, elementArgument, errorType);
        }
        Class<?> elementType = elementArgument.getType();
        Function<BodyElements<ByteBuffer<?>>, BodyElements<?>> elements;
        if (elementType == ByteBuffer.class) {
            elements = e -> e;
        } else if (conversionService.canConvert(ByteBuffer.class, elementType)) {
            elements = e -> MappedBodyElements.map(e, value -> conversionService.convert(value, elementType).orElseThrow());
        } else {
            return CompletableFuture.failedStage(new ConfigurationException("Cannot create the generated HTTP client's " +
                "required return type, since no TypeConverter from ByteBuffer to " +
                elementType + " is registered"));
        }
        if (exchange) {
            return asyncStreamingHttpClient.exchangeStream(request, errorType)
                .thenApply(response -> ElementsResponse.of(response, elements.apply(Objects.requireNonNull(response.body()))));
        }
        return asyncStreamingHttpClient.dataStream(request, errorType).thenApply(elements);
    }

    private CompletionStage<?> httpClientResponseStage(AsyncHttpClient asyncHttpClient,
                                                       MutableHttpRequest<?> request,
                                                       ReturnType<?> returnType,
                                                       Argument<?> errorType,
                                                       Argument<?> reactiveValueArgument) {
        Class<?> argumentType = reactiveValueArgument.getType();
        if (Void.class == argumentType || returnType.isVoid()) {
            request.getHeaders().remove(HttpHeaders.ACCEPT);
            return asyncHttpClient.retrieve(request, Argument.VOID, errorType);
        } else if (HttpResponse.class.isAssignableFrom(argumentType)) {
            return asyncHttpClient.exchange(request, reactiveValueArgument, errorType);
        } else {
            return asyncHttpClient.retrieve(request, reactiveValueArgument, errorType);
        }
    }

    @Nullable
    private Object getValue(Argument<?> argument,
                            MethodInvocationContext<?, ?> context,
                            Map<String, MutableArgumentValue<?>> parameters) {
        String argumentName = argument.getName();
        MutableArgumentValue<?> value = Objects.requireNonNull(parameters.get(argumentName));

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

        if (definedValue instanceof Optional<?> optional) {
            return optional.orElse(null);
        } else {
            return definedValue;
        }
    }

    @Nullable
    @SuppressWarnings("ReturnValueIgnored")
    private Object handleBlockingCall(String clientName, Class<?> returnType, Supplier<Object> supplier) {
        try {
            if (void.class == returnType) {
                supplier.get();
                return null;
            } else {
                return supplier.get();
            }
        } catch (RuntimeException t) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(HTTP_ERROR_RESPONSE_LOG_MESSAGE, clientName, t.getMessage(), t);
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
     * @param clientPath     The {@link Client#path()} value
     * @param clientId       The {@link Client#value()} value
     * @param templateString template to be applied
     * @return resolved template contents
     */
    private static String resolveTemplate(@Nullable String clientPath, @Nullable String clientId, String templateString) {
        if (StringUtils.isNotEmpty(clientPath)) {
            return clientPath + templateString;
        } else {
            if (StringUtils.isNotEmpty(clientId) && clientId.startsWith("/")) {
                return clientId + templateString;
            }
            return templateString;
        }
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

        static RequestBinderResult withRequest(MutableHttpRequest<?> request) {
            Objects.requireNonNull(request, "Bound HTTP request must not be null");
            return new RequestBinderResult(request, null, false);
        }

        static RequestBinderResult withErrorResult(@Nullable Object errorResult) {
            return new RequestBinderResult(null, errorResult, true);
        }
    }

    /**
     * The annotation values of a client method that may contain property placeholders. They are
     * read once to compute the {@link ClientMethod} and again on every call when the metadata has
     * property expressions, to detect a change after an environment refresh.
     *
     * @param mappedUri    The value of the HTTP method mapping annotation
     * @param customMethod The {@link CustomHttpMethod#method()} value
     * @param clientPath   The {@link Client#path()} value
     * @param clientId     The {@link Client#value()} value
     * @param consumes     The {@link Consumes} values
     * @param produces     The {@link Produces} values
     */
    private record MethodValues(
        String mappedUri,
        @Nullable String customMethod,
        @Nullable String clientPath,
        @Nullable String clientId,
        List<String> consumes,
        List<String> produces
    ) {

        static MethodValues read(MethodInvocationContext<Object, Object> context) {
            AnnotationValue<HttpMethodMapping> mapping = Objects.requireNonNull(context.getAnnotation(HttpMethodMapping.class));
            return new MethodValues(
                mapping.getRequiredValue(String.class),
                context.stringValue(CustomHttpMethod.class, "method").orElse(null),
                context.stringValue(Client.class, "path").orElse(null),
                context.stringValue(Client.class).orElse(null),
                Arrays.asList(context.stringValues(Consumes.class)),
                Arrays.asList(context.stringValues(Produces.class))
            );
        }
    }

    /**
     * The URI template of a client method and the values derived from it.
     *
     * @param uriTemplate     The parsed URI template
     * @param variableNames   The names of the template variables
     * @param tracingTemplate The template recorded on the request for tracing purposes
     */
    private record UriBinding(
        UriMatchTemplate uriTemplate,
        List<String> variableNames,
        String tracingTemplate
    ) {
    }

    /**
     * The data of a client method call that does not depend on the argument values. The values
     * that were computed while binding the request (the URI template and the media types) are
     * computed on first use, so a failure still happens while binding and is not cached.
     */
    private static final class ClientMethod {

        private static final ClientMethod NOT_MAPPED = new ClientMethod(false, false, null, HttpMethod.CUSTOM, "", "", HttpClient.DEFAULT_ERROR_TYPE, List.of(), true);

        private final boolean mapped;
        private final boolean revalidate;
        private final @Nullable MethodValues values;
        private final HttpMethod httpMethod;
        private final String httpMethodName;
        private final String uri;
        private final Argument<?> errorType;
        private final List<Class<? extends Annotation>> methodBinderTypes;
        private final boolean clientDefinition;
        // The lazily computed values below are immutable once published (the array is never
        // modified), so volatile is enough to publish them safely. A race only computes them twice.
        @SuppressWarnings("java:S3077")
        private volatile @Nullable UriBinding uriBinding;
        @SuppressWarnings("java:S3077")
        private volatile MediaType @Nullable [] acceptTypes;
        @SuppressWarnings("java:S3077")
        private volatile @Nullable MediaType contentType;

        private ClientMethod(boolean mapped,
                             boolean revalidate,
                             @Nullable MethodValues values,
                             HttpMethod httpMethod,
                             String httpMethodName,
                             String uri,
                             Argument<?> errorType,
                             List<Class<? extends Annotation>> methodBinderTypes,
                             boolean clientDefinition) {
            this.mapped = mapped;
            this.revalidate = revalidate;
            this.values = values;
            this.httpMethod = httpMethod;
            this.httpMethodName = httpMethodName;
            this.uri = uri;
            this.errorType = errorType;
            this.methodBinderTypes = methodBinderTypes;
            this.clientDefinition = clientDefinition;
        }

        /**
         * Computes the client method data.
         *
         * @param context            The invocation context
         * @param annotationMetadata The annotation metadata of the method
         * @param values             The values already read for the method or {@code null}
         * @return The client method data
         */
        static ClientMethod of(MethodInvocationContext<Object, Object> context,
                               AnnotationMetadata annotationMetadata,
                               @Nullable MethodValues values) {
            Optional<Class<? extends Annotation>> httpMethodMapping = context.getAnnotationTypeByStereotype(HttpMethodMapping.class);
            if (httpMethodMapping.isEmpty() || !context.hasStereotype(HttpMethodMapping.class)) {
                return NOT_MAPPED;
            }
            MethodValues methodValues = values != null ? values : MethodValues.read(context);
            String mappedUri = methodValues.mappedUri();
            String uri = StringUtils.isEmpty(mappedUri) ? "/" + context.getMethodName() : mappedUri;

            Class<? extends Annotation> annotationType = httpMethodMapping.get();
            HttpMethod httpMethod = HttpMethod.parse(annotationType.getSimpleName().toUpperCase(Locale.ENGLISH));
            String customMethod = methodValues.customMethod();
            String httpMethodName = customMethod != null ? customMethod : httpMethod.name();

            Argument<?> errorType = annotationMetadata.classValue(Client.class, "errorType")
                .<Argument<?>>map(Argument::of).orElse(HttpClient.DEFAULT_ERROR_TYPE);

            List<Class<? extends Annotation>> methodBinderTypes = new ArrayList<>(context.getAnnotationTypesByStereotype(Bindable.class));
            // @Version is not a bindable, so it needs to looked for separately
            methodBinderTypes.addAll(context.getAnnotationTypesByStereotype(Version.class));

            var definitionType = annotationMetadata.enumValue(Client.class, "definitionType", Client.DefinitionType.class)
                .orElse(Client.DefinitionType.CLIENT);

            return new ClientMethod(
                true,
                annotationMetadata.hasPropertyExpressions(),
                methodValues,
                httpMethod,
                httpMethodName,
                uri,
                errorType,
                Collections.unmodifiableList(methodBinderTypes),
                definitionType.isClient()
            );
        }

        boolean mapped() {
            return mapped;
        }

        boolean revalidate() {
            return revalidate;
        }

        @Nullable
        MethodValues values() {
            return values;
        }

        HttpMethod httpMethod() {
            return httpMethod;
        }

        String httpMethodName() {
            return httpMethodName;
        }

        Argument<?> errorType() {
            return errorType;
        }

        List<Class<? extends Annotation>> methodBinderTypes() {
            return methodBinderTypes;
        }

        UriBinding uriBinding() {
            UriBinding binding = uriBinding;
            if (binding == null) {
                UriMatchTemplate uriTemplate = UriMatchTemplate.of("");
                if (!(uri.length() == 1 && uri.charAt(0) == '/')) {
                    uriTemplate = uriTemplate.nest(uri);
                }
                MethodValues methodValues = Objects.requireNonNull(values);
                binding = new UriBinding(
                    uriTemplate,
                    Collections.unmodifiableList(uriTemplate.getVariableNames()),
                    resolveTemplate(methodValues.clientPath(), methodValues.clientId(), uriTemplate.toString())
                );
                uriBinding = binding;
            }
            return binding;
        }

        MediaType[] acceptTypes() {
            MediaType[] types = acceptTypes;
            if (types == null) {
                MethodValues methodValues = Objects.requireNonNull(values);
                List<String> consumesMediaType = clientDefinition ? methodValues.consumes() : methodValues.produces();
                if (consumesMediaType.isEmpty()) {
                    types = DEFAULT_ACCEPT_TYPES;
                } else {
                    types = MediaType.of(consumesMediaType.toArray(String[]::new));
                }
                acceptTypes = types;
            }
            return types;
        }

        MediaType contentType() {
            MediaType type = contentType;
            if (type == null) {
                MethodValues methodValues = Objects.requireNonNull(values);
                List<String> producesMediaType = clientDefinition ? methodValues.produces() : methodValues.consumes();
                MediaType[] contentTypes = MediaType.of(producesMediaType.toArray(String[]::new));
                type = ArrayUtils.isEmpty(contentTypes) ? MediaType.APPLICATION_JSON_TYPE : contentTypes[0];
                contentType = type;
            }
            return type;
        }
    }

    /**
     * Recovers {@link PropagatedContext} from the Kotlin coroutine context for suspend
     * client calls. Only called by the caller once it has already established, via
     * {@code instanceof} {@link KotlinInterceptedMethod}, that the current call is a
     * Kotlin suspend function (the same guard {@link io.micronaut.aop.internal.intercepted
     * .KotlinInterceptedMethodImpl} relies on before touching Kotlin coroutine types).
     */
    private static final class KotlinClientPropagatedContext {
        private KotlinClientPropagatedContext() {
        }

        /**
         * If {@code kotlinInterceptedMethod} carries a non-empty unbound
         * {@link PropagatedContext} in its coroutine context, open a thread-bound scope.
         * Otherwise return {@code null} (caller skips close).
         */
        static PropagatedContext.@Nullable Scope maybePropagate(KotlinInterceptedMethod kotlinInterceptedMethod) {
            PropagatedContext fromCoroutine =
                io.micronaut.core.async.propagation.KotlinCoroutinePropagation.Companion
                    .findPropagatedContext(kotlinInterceptedMethod.getCoroutineContext());
            if (fromCoroutine == null || fromCoroutine.isEmpty() || fromCoroutine.isBound()) {
                return null;
            }
            return fromCoroutine.propagate();
        }
    }
}
