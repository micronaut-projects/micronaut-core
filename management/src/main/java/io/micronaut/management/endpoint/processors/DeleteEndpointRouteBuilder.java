package io.micronaut.management.endpoint.processors;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.management.endpoint.Endpoint;
import io.micronaut.management.endpoint.Delete;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;

/**
 * A processor that processes references to {@link Delete} operations {@link Endpoint} instances
 *
 * @author Graeme Rocher
 * @since 1.00
 */
@Singleton
public class DeleteEndpointRouteBuilder extends AbstractEndpointRouteBuilder {

    public DeleteEndpointRouteBuilder(ApplicationContext beanContext, UriNamingStrategy uriNamingStrategy, ConversionService<?> conversionService) {
        super(beanContext, uriNamingStrategy, conversionService);
    }

    @Override
    protected Class<? extends Annotation> getSupportedAnnotation() {
        return Delete.class;
    }

    @Override
    protected void registerRoute(ExecutableMethod<?, ?> method, String id) {
        Class<?> declaringType = method.getDeclaringType();
        UriTemplate template = buildUriTemplate(method, id);
        DELETE(template.toString(), declaringType, method.getMethodName(), method.getArgumentTypes());
    }
}