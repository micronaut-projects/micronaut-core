package io.micronaut.http.client.filter;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.PathMatcher;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.HttpFilterQualifier;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.HttpFilterResolver;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.*;

@Internal
@Prototype
@BootstrapContextCompatible
public class HttpClientFilterResolver implements HttpFilterResolver {

    private final AnnotationMetadataResolver annotationMetadataResolver;
    private final List<HttpClientFilter> clientFilters;
    private final Collection<String> clientIdentifiers;
    private final AnnotationValue annotationValue;

    public HttpClientFilterResolver(
            @Parameter @Nullable Collection<String> clientIdentifiers,
            @Parameter @Nullable AnnotationValue annotationValue,
            @Nullable AnnotationMetadataResolver annotationMetadataResolver,
            List<HttpClientFilter> clientFilters) {
        if (clientIdentifiers == null) {
            this.clientIdentifiers = Collections.emptyList();
        } else {
            this.clientIdentifiers = clientIdentifiers;
        }
        this.annotationValue = annotationValue;
        if (annotationMetadataResolver == null) {
            this.annotationMetadataResolver = AnnotationMetadataResolver.DEFAULT;
        } else {
            this.annotationMetadataResolver = annotationMetadataResolver;
        }
        this.clientFilters = clientFilters;
    }

    @Override
    public List<HttpClientFilter> resolveFilters(HttpRequest<?> request) {
        String requestPath = StringUtils.prependUri("/", request.getUri().getPath());
        io.micronaut.http.HttpMethod method = request.getMethod();
        List<HttpClientFilter> filterList = new ArrayList<>();
        for (HttpClientFilter filter : clientFilters) {
            if (filter instanceof Toggleable && !((Toggleable) filter).isEnabled()) {
                continue;
            }
            AnnotationMetadata annotationMetadata = annotationMetadataResolver.resolveMetadata(filter);
            Optional<AnnotationValue<Filter>> filterOpt = annotationMetadata.findAnnotation(Filter.class);

            if (annotationValue != null) {
                Optional<AnnotationValue<Annotation>> metaAnn = annotationMetadata.findAnnotation(annotationValue.getAnnotationName());
                if (metaAnn.isPresent()) {
                    if (metaAnn.get().equals(annotationValue)) {
                        filterList.add(filter);
                        continue;
                    }
                }
            }

            if (filterOpt.isPresent()) {
                AnnotationValue<Filter> filterAnn = filterOpt.get();
                String[] clients = filterAnn.get("serviceId", String[].class).orElse(null);
                if (ArrayUtils.isNotEmpty(clients)) {
                    if (!clientIdentifiers.isEmpty()) {
                        if (Arrays.stream(clients).noneMatch(id -> clientIdentifiers.contains(id))) {
                            // no matching clients
                            continue;
                        }
                    } else {
                        continue;
                    }
                }
                io.micronaut.http.HttpMethod[] methods = filterAnn.get("methods", io.micronaut.http.HttpMethod[].class, null);
                if (ArrayUtils.isNotEmpty(methods)) {
                    if (!Arrays.asList(methods).contains(method)) {
                        continue;
                    }
                }

                String[] patterns = filterAnn.stringValues();
                if (patterns.length == 0) {
                    filterList.add(filter);
                } else {
                    for (String pathPattern : patterns) {
                        if (PathMatcher.ANT.matches(pathPattern, requestPath)) {
                            filterList.add(filter);
                        }
                    }
                }
            } else {
                if (annotationMetadata.hasStereotype(HttpFilterQualifier.class)) {
                    continue;
                }
                filterList.add(filter);
            }
        }
        return filterList;
    }
}
