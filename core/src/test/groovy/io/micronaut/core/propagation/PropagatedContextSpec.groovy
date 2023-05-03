package io.micronaut.core.propagation

import spock.lang.Specification

class PropagatedContextSpec extends Specification {

    def "test multiple elements of the same type"() {
        given:
            PropagatedElement e1 = new PropagatedElement()
            PropagatedElement e2 = new PropagatedElement()
            PropagatedElement e3 = new PropagatedElement()
        expect:
            try (PropagatedContext.InContext ignore1 = PropagatedContext.getOrEmpty().plus(e1).propagate()) {
                try (PropagatedContext.InContext ignore2 = PropagatedContext.getOrEmpty().plus(e2).propagate()) {
                    try (PropagatedContext.InContext ignore3 = PropagatedContext.getOrEmpty().plus(e3).propagate()) {
                        PropagatedContext propagatedContext = PropagatedContext.get()
                        assert propagatedContext.get(PropagatedElement) == e3
                        assert propagatedContext.find(PropagatedElement).get() == e3
                        assert propagatedContext.getAllElements().contains(e1)
                        assert propagatedContext.getAllElements().contains(e2)
                        assert propagatedContext.getAllElements().contains(e3)
                        assert propagatedContext.findAll(PropagatedElement).toList() == [e3, e2, e1]
                    }
                }
            }

    }

    static class PropagatedElement implements PropagatedContextElement {
    }
}
