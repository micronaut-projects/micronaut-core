package io.micronaut.http.filter;

import io.micronaut.core.order.Ordered;

public sealed interface FilterOrder {
    /**
     * Compute the order value for the given bean.
     *
     * @param bean The bean to compute the order value for, potentially implementing
     * {@link Ordered}
     * @return The order value
     */
    int getOrder(Object bean);

    /**
     * Fixed order value.
     *
     * @param value The order value
     */
    record Fixed(int value) implements FilterOrder {
        @Override
        public int getOrder(Object bean) {
            return value;
        }
    }

    /**
     * Dynamic order value (from {@link Ordered#getOrder()}).
     *
     * @param fallbackValue The order value to use if the bean does not implement {@link Ordered}
     */
    record Dynamic(int fallbackValue) implements FilterOrder {
        @Override
        public int getOrder(Object bean) {
            if (bean instanceof Ordered o) {
                return o.getOrder();
            } else {
                return fallbackValue;
            }
        }
    }
}
