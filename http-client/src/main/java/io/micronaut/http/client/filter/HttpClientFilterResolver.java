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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.HttpFilterStereotype;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.http.filter.HttpFilterResolver;

import javax.annotation.Nullable;
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

            boolean matches = false;

            if (annotationValue != null) {
                matches = annotationMetadata.hasAnnotation(annotationValue.getAnnotationName());

                HttpMethod[] methods = annotationMetadata.findAnnotation(HttpFilterStereotype.class)
                        .flatMap(ann -> ann.get("methods", HttpMethod[].class))
                        .orElse(null);

                if (ArrayUtils.isNotEmpty(methods)) {
                    matches = matches && anyMethodMatches(method, methods);
                }
            }

            if (!matches && filterOpt.isPresent()) {
                AnnotationValue<Filter> filterAnn = filterOpt.get();

                String[] clients = filterAnn.get("serviceId", String[].class).orElse(null);
                HttpMethod[] methods = filterAnn.get("methods", HttpMethod[].class, null);
                String[] patterns = filterAnn.stringValues();

                boolean hasClients = ArrayUtils.isNotEmpty(clients);
                boolean hasMethods = ArrayUtils.isNotEmpty(methods);
                boolean hasPatterns = ArrayUtils.isNotEmpty(patterns);

                if (hasClients) {
                    matches = containsIndentifier(clients);
                    if (hasMethods) {
                        matches = matches && anyMethodMatches(method, methods);
                    }
                    if (hasPatterns) {
                        matches = matches && anyPatternMatches(requestPath, patterns);
                    }
                } else if (hasPatterns) {
                    matches = anyPatternMatches(requestPath, patterns);
                    if (hasMethods) {
                        matches = matches && anyMethodMatches(method, methods);
                    }
                }
            }

            if (!annotationMetadata.hasStereotype(HttpFilterStereotype.class) && !filterOpt.isPresent()) {
                matches = true;
            }

            if (matches) {
                filterList.add(filter);
            }
        }
        return filterList;
    }

    private boolean containsIndentifier(String[] clients) {
        return Arrays.stream(clients).anyMatch(clientIdentifiers::contains);
    }

    private boolean anyPatternMatches(String requestPath, String[] patterns) {
        return Arrays.stream(patterns).anyMatch(pattern -> PathMatcher.ANT.matches(pattern, requestPath));
    }

    private boolean anyMethodMatches(HttpMethod requestMethod, HttpMethod[] methods) {
        return Arrays.asList(methods).contains(requestMethod);
    }
}
