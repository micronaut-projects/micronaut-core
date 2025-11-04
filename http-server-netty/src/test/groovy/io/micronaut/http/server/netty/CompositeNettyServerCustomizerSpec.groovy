package io.micronaut.http.server.netty

import io.micronaut.core.order.Ordered
import spock.lang.Specification

class CompositeNettyServerCustomizerSpec extends Specification {
    def order() {
        given:
        def composite = new CompositeNettyServerCustomizer()

        def c1 = new OrderedCustomizer(Ordered.HIGHEST_PRECEDENCE) {
            boolean called = false

            @Override
            void onInitialPipelineBuilt() {
                called = true
            }
        }
        def calledInRightOrder = false
        def c2 = new OrderedCustomizer(Ordered.LOWEST_PRECEDENCE) {
            @Override
            void onInitialPipelineBuilt() {
                calledInRightOrder = c1.called
            }
        }
        composite.add(c2)
        composite.add(c1)

        when:
        composite.onInitialPipelineBuilt()
        then:
        calledInRightOrder
    }

    private static class OrderedCustomizer implements NettyServerCustomizer, Ordered {
        final int order

        OrderedCustomizer(int order) {
            this.order = order
        }

        @Override
        int getOrder() {
            return order
        }
    }
}
