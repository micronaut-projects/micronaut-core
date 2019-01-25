package io.micronaut.configuration.rabbitmq.bind;

import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.ArgumentBinderRegistry;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.*;

@Singleton
public class RabbitBinderRegistry implements ArgumentBinderRegistry<RabbitMessageState> {

    private final Map<Class<? extends Annotation>, ArgumentBinder<?, RabbitMessageState>> byAnnotation = new LinkedHashMap<>();
    private final Map<Integer, ArgumentBinder<?, RabbitMessageState>> byType = new LinkedHashMap<>();
    private final List<ArgumentBinder<?, RabbitMessageState>> otherBinders = new ArrayList<>();
    private final RabbitDefaultBinder defaultBinder;

    public RabbitBinderRegistry(RabbitDefaultBinder defaultBinder, RabbitArgumentBinder... binders) {
        this.defaultBinder = defaultBinder;
        if (ArrayUtils.isNotEmpty(binders)) {
            for (RabbitArgumentBinder binder : binders) {
                if (binder instanceof RabbitAnnotatedArgumentBinder) {
                    RabbitAnnotatedArgumentBinder<?> annotatedBinder = (RabbitAnnotatedArgumentBinder<?>) binder;
                    byAnnotation.put(
                            annotatedBinder.getAnnotationType(),
                            binder
                    );
                } else if (binder instanceof RabbitTypeArgumentBinder) {
                    RabbitTypeArgumentBinder<?> typedBinder = (RabbitTypeArgumentBinder<?>) binder;
                    byType.put(
                            typedBinder.argumentType().typeHashCode(),
                            typedBinder
                    );
                }
            }
        }
    }

    @Override
    public <T> Optional<ArgumentBinder<T, RabbitMessageState>> findArgumentBinder(Argument<T> argument, RabbitMessageState source) {

        Optional<Class<? extends Annotation>> opt = argument.getAnnotationMetadata().getAnnotationTypeByStereotype(Bindable.class);
        if (opt.isPresent()) {
            Class<? extends Annotation> annotationType = opt.get();
            ArgumentBinder binder = byAnnotation.get(annotationType);
            if (binder != null) {
                return Optional.of(binder);
            }
        } else {
            ArgumentBinder binder = byType.get(argument.typeHashCode());
            if (binder != null) {
                return Optional.of(binder);
            }
        }
        return Optional.of((ArgumentBinder<T, RabbitMessageState>) defaultBinder);
    }
}
