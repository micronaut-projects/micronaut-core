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

    def "test removing element"() {
        given:
            PropagatedElement e1 = new PropagatedElement()
            PropagatedElement e2 = new PropagatedElement()
            PropagatedElement e3 = new PropagatedElement()
        expect:
            assert PropagatedContext.getOrEmpty().findAll(PropagatedElement).toList() == []
            try (PropagatedContext.InContext ignore1 = PropagatedContext.getOrEmpty().plus(e1).propagate()) {
                assert PropagatedContext.get().findAll(PropagatedElement).toList() == [e1]
                try (PropagatedContext.InContext ignore2 = PropagatedContext.getOrEmpty().plus(e2).propagate()) {
                    assert PropagatedContext.get().findAll(PropagatedElement).toList() == [e2, e1]
                    try (PropagatedContext.InContext ignore3 = PropagatedContext.getOrEmpty().plus(e3).propagate()) {
                        assert PropagatedContext.get().findAll(PropagatedElement).toList() == [e3, e2, e1]
                        try (PropagatedContext.InContext ignore4 = PropagatedContext.getOrEmpty().minus(e2).propagate()) {
                            assert PropagatedContext.get().findAll(PropagatedElement).toList() == [e3, e1]
                        }
                        try (PropagatedContext.InContext ignore4 = PropagatedContext.getOrEmpty().minus(e2).minus(e1).propagate()) {
                            assert PropagatedContext.get().findAll(PropagatedElement).toList() == [e3]
                        }
                        assert PropagatedContext.get().findAll(PropagatedElement).toList() == [e3, e2, e1]
                    }
                    assert PropagatedContext.get().findAll(PropagatedElement).toList() == [e2, e1]
                }
                assert PropagatedContext.get().findAll(PropagatedElement).toList() == [e1]
            }
            assert PropagatedContext.getOrEmpty().findAll(PropagatedElement).toList() == []
    }

    def "test removing missing element"() {
        given:
            PropagatedElement e1 = new PropagatedElement()
        when:
            try (PropagatedContext.InContext ignore4 = PropagatedContext.getOrEmpty().minus(e1).propagate()) {
            }
        then:
            thrown(NoSuchElementException)
    }

    static class PropagatedElement implements PropagatedContextElement {
    }
}
