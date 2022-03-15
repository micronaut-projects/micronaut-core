package io.micronaut.core.annotation;

import java.util.Optional;
import java.util.function.Function;

/**
 * A reference to a bean property in annotation metadata.
 * Annotation property reference consists of bean property owning type, the property name (which is the original
 * annotation member value) and a function which can be used to retrieve bean property value
 *
 * @param <T> The generic type of the bean property owning class
 * @param <R> The generic type of bean property value
 * @author Sergey Gavrilov
 * @since 3.4.0
 */
public final class AnnotationPropertyReference<T, R> {

    private final AnnotationClassValue<T> owningType;
    private final String propertyName;
    private final Function<T, R> propertyGetter;

    /**
     * Constructs annotation property property reference.
     *
     * @param owningType     the type owning referenced bean property
     * @param propertyName   the name of reference bean property
     * @param propertyGetter the function which can be used to access bean property
     */
    public AnnotationPropertyReference(AnnotationClassValue<T> owningType,
                                       String propertyName,
                                       Function<T, R> propertyGetter) {
        this.owningType = owningType;
        this.propertyName = propertyName;
        this.propertyGetter = propertyGetter;
    }

    /**
     * @return bean property name
     */
    public String getPropertyName() {
        return propertyName;
    }

    /**
     * @return bean property owning type
     */
    public AnnotationClassValue<T> getPropertyOwningType() {
        return owningType;
    }

    /**
     * Used to obtain bean property value when from the provided bean.
     *
     * @param bean object which property is obtained using respective property getter
     * @return bean property value
     */
    @SuppressWarnings("unchecked")
    public Optional<R> getPropertyValue(Object bean) {
        try {
            return Optional.ofNullable(propertyGetter.apply((T) bean));
        } catch (Throwable ex) {
            return Optional.empty();
        }
    }

}
