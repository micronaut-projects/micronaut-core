package io.micronaut.web.router;

import io.micronaut.context.BeanContext;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.context.ServerContextPathProvider;
import io.micronaut.http.filter.FilterOrder;
import io.micronaut.http.filter.FilterPatternStyle;
import io.micronaut.http.filter.InternalFilter;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

@Singleton
public class ServerFilterRouteBuilder extends DefaultRouteBuilder implements ExecutableMethodProcessor<ServerFilter> {
    private final BeanContext beanContext;
    private final ServerContextPathProvider contextPathProvider;

    /**
     * @param executionHandleLocator The execution handler locator
     * @param uriNamingStrategy      The URI naming strategy
     * @param conversionService      The conversion service
     * @param beanContext
     * @param contextPathProvider
     */
    public ServerFilterRouteBuilder(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy, ConversionService conversionService, BeanContext beanContext, ServerContextPathProvider contextPathProvider) {
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
            FilterRoute filter = addFilter(() -> new InternalFilter.Before<>(beanContext.getBean(beanDefinition), method, combined.order), method);
            applyMetadata(filter, combined);
        }
        if (method.isAnnotationPresent(ResponseFilter.class)) {
            FilterMetadata methodLevel = metadata(method, ResponseFilter.class);
            FilterMetadata combined = combineMetadata(beanLevel, methodLevel);
            FilterRoute filter = addFilter(() -> new InternalFilter.After<>(beanContext.getBean(beanDefinition), method, combined.order), method);
            applyMetadata(filter, combined);
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
            // todo
            throw new UnsupportedOperationException();
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
            order
        );
    }

    private FilterMetadata metadata(AnnotationMetadata annotationMetadata, Class<? extends Annotation> annotationType) {
        HttpMethod[] methods = annotationMetadata.enumValues(annotationType, "methods", HttpMethod.class);
        String[] patterns = annotationMetadata.stringValues(annotationType);
        OptionalInt order = annotationMetadata.intValue(Order.class);
        return new FilterMetadata(
            annotationMetadata.enumValue(annotationType, "patternStyle", FilterPatternStyle.class).orElse(FilterPatternStyle.ANT),
            ArrayUtils.isNotEmpty(patterns) ? Arrays.asList(patterns) : null,
            ArrayUtils.isNotEmpty(methods) ? Arrays.asList(methods) : null,
            order.isPresent() ? new FilterOrder.Fixed(order.getAsInt()) : null
        );
    }

    /**
     * @param beanDefinition The bean definition
     * @return The array of patterns that should match request URLs for the bean to
     * be invoked.
     */
    private String[] getPatterns(AnnotationMetadata beanDefinition) {
        String[] values = beanDefinition.stringValues(Filter.class);
        String contextPath = contextPathProvider != null ? contextPathProvider.getContextPath() : null;
        if (contextPath != null) {
            for (int i = 0; i < values.length; i++) {
                if (!values[i].startsWith(contextPath)) {
                    String newValue = StringUtils.prependUri(contextPath, values[i]);
                    if (newValue.charAt(0) != '/') {
                        newValue = "/" + newValue;
                    }
                    values[i] = newValue;
                }
            }
        }
        return values;
    }

    private record FilterMetadata(
        FilterPatternStyle patternStyle,
        @Nullable List<String> patterns,
        @Nullable List<HttpMethod> methods,
        @Nullable FilterOrder order
    ) {}
}
