package org.particleframework.management.endpoint.processors;

import org.particleframework.context.ApplicationContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.uri.UriTemplate;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.management.endpoint.Endpoint;
import org.particleframework.management.endpoint.Delete;

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