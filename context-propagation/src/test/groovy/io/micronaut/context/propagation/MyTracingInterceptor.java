package io.micronaut.context.propagation;

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
@InterceptorBean(MyTrace.class)
class MyTracingInterceptor implements MethodInterceptor<Object, Object> {

    List<Trace> traces = new ArrayList<>();

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Trace trace = new Trace();
        traces.add(trace);
        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty()
                .plus(new TracePropagatedContext(trace))
                .propagate()) {
            InterceptedMethod interceptedMethod = InterceptedMethod.of(context, ConversionService.SHARED);
            return switch (interceptedMethod.resultType()) {
                case PUBLISHER -> interceptedMethod.handleResult(
                    interceptedMethod.interceptResultAsPublisher()
                );
                case SYNCHRONOUS, COMPLETION_STAGE -> interceptedMethod.handleResult(
                    interceptedMethod.interceptResult()
                );
            };
        }
    }

    public Trace getCurrentTrace() {
        return PropagatedContext.getOrEmpty().find(TracePropagatedContext.class).orElseThrow().trace();
    }

    public Optional<Trace> findCurrentTrace() {
        return PropagatedContext.getOrEmpty().find(TracePropagatedContext.class).map(TracePropagatedContext::trace);
    }

    private record TracePropagatedContext(Trace trace) implements PropagatedContextElement {
    }

    static class Trace {

        private final Map<String, String> tags = new HashMap<>();

        public void tag(String s1, String s2) {
            tags.put(s1, s2);
        }

        public Map<String, String> tags() {
            return tags;
        }
    }
}
