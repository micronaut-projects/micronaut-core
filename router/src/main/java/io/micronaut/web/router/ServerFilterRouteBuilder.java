package io.micronaut.web.router;

import io.micronaut.context.BeanContext;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.AntPathMatcher;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.context.ServerContextPathProvider;
import io.micronaut.http.filter.FilterOrder;
import io.micronaut.http.filter.FilterPatternStyle;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.Executor;

/**
 * {@link RouteBuilder} for {@link ServerFilter}s.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Singleton
@Experimental
public class ServerFilterRouteBuilder extends DefaultRouteBuilder implements ExecutableMethodProcessor<ServerFilter> {
    private final BeanContext beanContext;
    private final ServerContextPathProvider contextPathProvider;

    /**
     * @param executionHandleLocator The execution handler locator
     * @param uriNamingStrategy      The URI naming strategy
     * @param conversionService      The conversion service
     * @param beanContext            The bean context
     * @param contextPathProvider    The server context path provider
     */
    public ServerFilterRouteBuilder(
        ExecutionHandleLocator executionHandleLocator,
        UriNamingStrategy uriNamingStrategy,
        ConversionService conversionService,
        BeanContext beanContext,
        @Nullable ServerContextPathProvider contextPathProvider
    ) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
        this.beanContext = beanContext;
        this.contextPathProvider = contextPathProvider;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        //noinspection unchecked,rawtypes
        process0(beanDefinition, (ExecutableMethod) method);
    }

    public <T> void process0(BeanDefinition<T> beanDefinition, ExecutableMethod<T, ?> method) {
        FilterMetadata beanLevel = metadata(beanDefinition, ServerFilter.class);
        if (method.isAnnotationPresent(RequestFilter.class)) {
            FilterMetadata methodLevel = metadata(method, RequestFilter.class);
            FilterMetadata combined = combineMetadata(beanLevel, methodLevel);
            FilterRoute filter = addFilter(() -> withAsync(combined, new GenericHttpFilter.Before<>(beanContext.getBean(beanDefinition), method, combined.order)), method);
            applyMetadata(filter, combined);
        }
        if (method.isAnnotationPresent(ResponseFilter.class)) {
            FilterMetadata methodLevel = metadata(method, ResponseFilter.class);
            FilterMetadata combined = combineMetadata(beanLevel, methodLevel);
            FilterRoute filter = addFilter(() -> withAsync(combined, new GenericHttpFilter.After<>(beanContext.getBean(beanDefinition), method, combined.order)), method);
            applyMetadata(filter, combined);
        }
    }

    private GenericHttpFilter withAsync(FilterMetadata metadata, GenericHttpFilter filter) {
        if (metadata.executeOn != null) {
            return new GenericHttpFilter.Async(filter, beanContext.getBean(Executor.class, Qualifiers.byName(metadata.executeOn)));
        } else {
            return filter;
        }
    }

    private void applyMetadata(FilterRoute route, FilterMetadata metadata) {
        route.patternStyle(metadata.patternStyle);
        // todo: handle missing patterns
        for (String pattern : Objects.requireNonNull(metadata.patterns, "patterns")) {
            route.pattern(pattern);
        }
        if (metadata.methods != null) {
            route.methods(metadata.methods.toArray(new HttpMethod[0]));
        }
    }

    private FilterMetadata combineMetadata(FilterMetadata beanLevel, FilterMetadata methodLevel) {
        List<String> patterns;
        if (beanLevel.patterns == null) {
            patterns = methodLevel.patterns;
        } else if (methodLevel.patterns == null) {
            patterns = beanLevel.patterns;
        } else {
            if (beanLevel.patternStyle == FilterPatternStyle.REGEX ||
                methodLevel.patternStyle == FilterPatternStyle.REGEX) {
                throw new UnsupportedOperationException("Concatenating regex filter patterns is " +
                    "not supported. Please declare the full pattern on the method instead.");
            }
            patterns = beanLevel.patterns.stream()
                .flatMap(p1 -> methodLevel.patterns.stream().map(p2 -> concatAntPatterns(p1, p2)))
                .toList();
        }

        String contextPath = contextPathProvider != null ? contextPathProvider.getContextPath() : null;
        if (contextPath != null && patterns != null) {
            patterns = patterns.stream()
                .map(pattern -> {
                    if (!pattern.startsWith(contextPath)) {
                        String newValue = StringUtils.prependUri(contextPath, pattern);
                        if (newValue.charAt(0) != '/') {
                            newValue = "/" + newValue;
                        }
                        return newValue;
                    } else {
                        return pattern;
                    }
                })
                .toList();
        }

        FilterOrder order;
        if (methodLevel.order != null) {
            order = methodLevel.order;
        } else if (beanLevel.order != null) {
            // allow overriding using Ordered.getOrder, where possible
            order = new FilterOrder.Dynamic(((FilterOrder.Fixed) beanLevel.order).value());
        } else {
            order = new FilterOrder.Dynamic(Ordered.LOWEST_PRECEDENCE);
        }

        return new FilterMetadata(
            methodLevel.patterns == null ? beanLevel.patternStyle : methodLevel.patternStyle,
            patterns,
            methodLevel.methods == null ? beanLevel.methods : methodLevel.methods,
            order,
            methodLevel.executeOn == null ? beanLevel.executeOn : methodLevel.executeOn
        );
    }

    static String concatAntPatterns(String p1, String p2) {
        StringBuilder combined = new StringBuilder(p1.length() + p2.length() + 1);
        combined.append(p1);
        if (!p1.endsWith(AntPathMatcher.DEFAULT_PATH_SEPARATOR)) {
            combined.append(AntPathMatcher.DEFAULT_PATH_SEPARATOR);
        }
        if (p2.startsWith(AntPathMatcher.DEFAULT_PATH_SEPARATOR)) {
            combined.append(p2, AntPathMatcher.DEFAULT_PATH_SEPARATOR.length(), p2.length());
        } else {
            combined.append(p2);
        }
        return combined.toString();
    }

    private FilterMetadata metadata(AnnotationMetadata annotationMetadata, Class<? extends Annotation> annotationType) {
        HttpMethod[] methods = annotationMetadata.enumValues(annotationType, "methods", HttpMethod.class);
        String[] patterns = annotationMetadata.stringValues(annotationType);
        OptionalInt order = annotationMetadata.intValue(Order.class);
        return new FilterMetadata(
            annotationMetadata.enumValue(annotationType, "patternStyle", FilterPatternStyle.class).orElse(FilterPatternStyle.ANT),
            ArrayUtils.isNotEmpty(patterns) ? Arrays.asList(patterns) : null,
            ArrayUtils.isNotEmpty(methods) ? Arrays.asList(methods) : null,
            order.isPresent() ? new FilterOrder.Fixed(order.getAsInt()) : null,
            annotationMetadata.stringValue(ExecuteOn.class).orElse(null)
        );
    }

    private record FilterMetadata(
        FilterPatternStyle patternStyle,
        @Nullable List<String> patterns,
        @Nullable List<HttpMethod> methods,
        @Nullable FilterOrder order,
        @Nullable String executeOn
    ) {}
}
