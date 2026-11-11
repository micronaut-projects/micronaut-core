package io.micronaut.aop.async;

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Proceeds in a propagated context that holds a {@link Marker}.
 */
@Singleton
@InterceptorBean(Propagating.class)
public class PropagatingInterceptor implements MethodInterceptor<Object, Object> {

    private final ConversionService conversionService;

    public PropagatingInterceptor(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(new Marker("marked")).propagate()) {
            return interceptedMethod.handleResult(interceptedMethod.interceptResult());
        } catch (Exception e) {
            return interceptedMethod.handleException(e);
        }
    }

    /**
     * The element the interceptor propagates.
     *
     * @param value The value
     */
    public record Marker(String value) implements PropagatedContextElement {
    }
}
