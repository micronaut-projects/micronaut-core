package io.micronaut.aop.chain;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InvocationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract interceptor chain implementation.
 *
 * @param <B> The bean type
 * @param <R> The return type
 * @author graemerocher
 * @since 3.0.0
 */
@Internal
abstract class AbstractInterceptorChain<B, R> implements InvocationContext<B, R> {
    protected static final Logger LOG = LoggerFactory.getLogger(InterceptorChain.class);
    protected final Interceptor<B, R>[] interceptors;
    protected final Object[] originalParameters;
    protected final int interceptorCount;
    protected MutableConvertibleValues<Object> attributes;
    protected int index = 0;
    protected Map<String, MutableArgumentValue<?>> parameters;

    AbstractInterceptorChain(Interceptor<B, R>[] interceptors, Object... originalParameters) {
        this.interceptors = interceptors;
        this.interceptorCount = interceptors.length;
        this.originalParameters = originalParameters;
    }

    @Override
    public @NonNull Object[] getParameterValues() {
        return originalParameters;
    }

    @Override
    public @NonNull MutableConvertibleValues<Object> getAttributes() {
        MutableConvertibleValues<Object> attributes = this.attributes;
        if (attributes == null) {
            synchronized (this) { // double check
                attributes = this.attributes;
                if (attributes == null) {
                    attributes = MutableConvertibleValues.of(new ConcurrentHashMap<>(5));
                    this.attributes = attributes;
                }
            }
        }
        return attributes;
    }

    @Override
    public @NonNull Map<String, MutableArgumentValue<?>> getParameters() {
        Map<String, MutableArgumentValue<?>>  parameters = this.parameters;
        if (parameters == null) {
            synchronized (this) { // double check
                parameters = this.parameters;
                if (parameters == null) {
                    Argument[] arguments = getArguments();
                    parameters = new LinkedHashMap<>(arguments.length);
                    for (int i = 0; i < arguments.length; i++) {
                        Argument argument = arguments[i];
                        int finalIndex = i;
                        parameters.put(argument.getName(), new MutableArgumentValue<Object>() {
                            @Override
                            public AnnotationMetadata getAnnotationMetadata() {
                                return argument.getAnnotationMetadata();
                            }

                            @Override
                            public Optional<Argument<?>> getFirstTypeVariable() {
                                return argument.getFirstTypeVariable();
                            }

                            @Override
                            public Argument[] getTypeParameters() {
                                return argument.getTypeParameters();
                            }

                            @Override
                            public Map<String, Argument<?>> getTypeVariables() {
                                return argument.getTypeVariables();
                            }

                            @NonNull
                            @Override
                            public String getName() {
                                return argument.getName();
                            }

                            @NonNull
                            @Override
                            public Class<Object> getType() {
                                return argument.getType();
                            }

                            @Override
                            public boolean equalsType(Argument<?> other) {
                                return argument.equalsType(other);
                            }

                            @Override
                            public int typeHashCode() {
                                return argument.typeHashCode();
                            }

                            @Override
                            public Object getValue() {
                                return originalParameters[finalIndex];
                            }

                            @Override
                            public void setValue(Object value) {
                                originalParameters[finalIndex] = value;
                            }
                        });
                    }
                    parameters = Collections.unmodifiableMap(parameters);
                    this.parameters = parameters;
                }
            }
        }
        return parameters;
    }

    @Override
    public R proceed(@NonNull Interceptor from) throws RuntimeException {
        for (int i = 0; i < interceptors.length; i++) {
            Interceptor<B, R> interceptor = interceptors[i];
            if (interceptor == from) {
                index = i + 1;
                return proceed();

            }
        }
        throw new IllegalArgumentException("Argument [" + from + "] is not within the interceptor chain");
    }
}
