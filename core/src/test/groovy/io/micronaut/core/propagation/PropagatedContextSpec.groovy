package io.micronaut.core.propagation

import spock.lang.Specification

class PropagatedContextSpec extends Specification {

    def "test empty"() {
        given:
            PropagatedElement e1 = new PropagatedElement()
            PropagatedElement e2 = new PropagatedElement()
            PropagatedElement e3 = new PropagatedElement()
        expect:
            try (PropagatedContext.Scope ignore1 = PropagatedContext.getOrEmpty().plus(e1).propagate()) {
                try (PropagatedContext.Scope ignore2 = PropagatedContext.getOrEmpty().plus(e2).propagate()) {
                    try (PropagatedContext.Scope ignore3 = PropagatedContext.getOrEmpty().plus(e3).propagate()) {
                        try (PropagatedContext.Scope ignore4 = PropagatedContext.empty().propagate()) {
                            PropagatedContext propagatedContext = PropagatedContext.getOrEmpty()
                            assert propagatedContext.getAllElements() == []
                            assert !PropagatedContext.exists()
                        }
                    }
                }
            }
    }

    def "test minus"() {
        given:
            PropagatedElement e1 = new PropagatedElement()
            PropagatedElement e2 = new PropagatedElement()
            PropagatedElement e3 = new PropagatedElement()
        expect:
            try (PropagatedContext.Scope ignore1 = PropagatedContext.getOrEmpty().plus(e1).propagate()) {
                try (PropagatedContext.Scope ignore2 = PropagatedContext.getOrEmpty().plus(e2).propagate()) {
                    try (PropagatedContext.Scope ignore3 = PropagatedContext.getOrEmpty().plus(e3).propagate()) {
                        PropagatedContext propagatedContext = PropagatedContext.get()
                        assert propagatedContext.getAllElements() == [e1, e2, e3]
                        assert propagatedContext.minus(e1).getAllElements() == [e2, e3]
                        assert propagatedContext.minus(e2).getAllElements() == [e1, e3]
                        assert propagatedContext.minus(e3).getAllElements() == [e1, e2]
                        assert propagatedContext.minus(e1).minus(e2).getAllElements() == [e3]
                        assert propagatedContext.minus(e1).minus(e2).minus(e3).getAllElements() == []
                    }
                }
            }
    }

    def "test multiple elements of the same type"() {
        given:
            PropagatedElement e1 = new PropagatedElement()
            PropagatedElement e2 = new PropagatedElement()
            PropagatedElement e3 = new PropagatedElement()
        expect:
            try (PropagatedContext.Scope ignore1 = PropagatedContext.getOrEmpty().plus(e1).propagate()) {
                try (PropagatedContext.Scope ignore2 = PropagatedContext.getOrEmpty().plus(e2).propagate()) {
                    try (PropagatedContext.Scope ignore3 = PropagatedContext.getOrEmpty().plus(e3).propagate()) {
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
            try (PropagatedContext.Scope ignore1 = PropagatedContext.getOrEmpty().plus(e1).propagate()) {
                assert PropagatedContext.get().findAll(PropagatedElement).toList() == [e1]
                try (PropagatedContext.Scope ignore2 = PropagatedContext.getOrEmpty().plus(e2).propagate()) {
                    assert PropagatedContext.get().findAll(PropagatedElement).toList() == [e2, e1]
                    try (PropagatedContext.Scope ignore3 = PropagatedContext.getOrEmpty().plus(e3).propagate()) {
                        assert PropagatedContext.get().findAll(PropagatedElement).toList() == [e3, e2, e1]
                        try (PropagatedContext.Scope ignore4 = PropagatedContext.getOrEmpty().minus(e2).propagate()) {
                            assert PropagatedContext.get().findAll(PropagatedElement).toList() == [e3, e1]
                        }
                        try (PropagatedContext.Scope ignore4 = PropagatedContext.getOrEmpty().minus(e2).minus(e1).propagate()) {
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

    def "test replacing element"() {
        given:
            PropagatedElement e1 = new PropagatedElement()
            PropagatedElement e2 = new PropagatedElement()
            PropagatedElement e3 = new PropagatedElement()
            PropagatedElement e4 = new PropagatedElement()
        expect:
            assert PropagatedContext.getOrEmpty().findAll(PropagatedElement).toList() == []
            try (PropagatedContext.Scope ignore1 = PropagatedContext.getOrEmpty().plus(e1).propagate()) {
                assert PropagatedContext.get().findAll(PropagatedElement).toList() == [e1]
                try (PropagatedContext.Scope ignore2 = PropagatedContext.getOrEmpty().plus(e2).propagate()) {
                    assert PropagatedContext.get().findAll(PropagatedElement).toList() == [e2, e1]
                    try (PropagatedContext.Scope ignore3 = PropagatedContext.getOrEmpty().plus(e3).propagate()) {
                        assert PropagatedContext.get().findAll(PropagatedElement).toList() == [e3, e2, e1]
                        try (PropagatedContext.Scope ignore4 = PropagatedContext.getOrEmpty().replace(e2, e4).propagate()) {
                            assert PropagatedContext.get().findAll(PropagatedElement).toList() == [e3, e4, e1]
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
            try (PropagatedContext.Scope ignore4 = PropagatedContext.getOrEmpty().minus(e1).propagate()) {
            }
        then:
            thrown(NoSuchElementException)
    }

    def "test replacing missing element"() {
        given:
            PropagatedElement e1 = new PropagatedElement()
            PropagatedElement e2 = new PropagatedElement()
        when:
            try (PropagatedContext.Scope ignore4 = PropagatedContext.getOrEmpty().replace(e1, e2).propagate()) {
            }
        then:
            thrown(NoSuchElementException)
    }

    def "test empty propagated context cleans the context"() {
        given:
            PropagatedElement e1 = new PropagatedElement()
        expect:
            assert PropagatedContext.getOrEmpty().allElements.size() == 0
            try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().propagate()) {
                PropagatedContext.getOrEmpty().plus(e1).propagate()
                assert PropagatedContext.getOrEmpty().allElements.size() == 1
            }
            assert PropagatedContext.getOrEmpty().allElements.size() == 0
    }

    def "test empty propagated context restores the context"() {
        given:
            PropagatedElement e1 = new PropagatedElement()
            PropagatedElement e = new PropagatedElement()
        expect:
            try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(e).propagate()) {
                assert PropagatedContext.getOrEmpty().allElements.size() == 1
                try (PropagatedContext.Scope ignore2 = PropagatedContext.getOrEmpty().propagate()) {
                    PropagatedContext.getOrEmpty().plus(e1).propagate()
                    assert PropagatedContext.getOrEmpty().allElements.size() == 2
                }
                assert PropagatedContext.getOrEmpty().allElements.size() == 1
            }
            assert PropagatedContext.getOrEmpty().allElements.size() == 0
    }

    def "test restoring thread context"() {
        given:
        String before = ''
        String inside, after
        CONTEXT_NAME_HOLDER.set(before)
        PropagatedContext context = PropagatedContext.empty()
                .plus(new SetContextName('one', strict))
                .plus(new SetContextName('two', strict))

        when:
        try (def ignore = context.propagate()) {
            inside = currentContextName
        }
        after = currentContextName

        then:
        inside == 'two'
        after == before

        where:
        strict << [false, true]
    }

    def "test restoring thread context in a chain"() {
        given:
        String before = ''
        String firstIn, firstOut, secondIn, secondOut
        CONTEXT_NAME_HOLDER.set(before)

        when:
        try (def ignore1 = PropagatedContext.empty().plus(new SetContextName('first', strict)).propagate()) {
            firstIn = currentContextName
            try (def ignore2 = PropagatedContext.get().plus(new SetContextName('second', strict)).propagate()) {
                secondIn = currentContextName
            }
            secondOut = currentContextName
        }
        firstOut = currentContextName

        then:
        firstIn == 'first'
        secondIn == 'second'
        secondOut == 'first'
        firstOut == before

        where:
        strict << [false, true]
    }

    static class PropagatedElement implements PropagatedContextElement {
    }

    static final ThreadLocal<String> CONTEXT_NAME_HOLDER = ThreadLocal.withInitial { '' }

    static String getCurrentContextName() {
        return CONTEXT_NAME_HOLDER.get()
    }

    static record SetContextName(String name, boolean strict) implements ThreadPropagatedContextElement<String> {

        @Override
        String updateThreadContext() {
            String current = getCurrentContextName()
            CONTEXT_NAME_HOLDER.set(name)
            return current
        }

        @Override
        void restoreThreadContext(String oldState) {
            String current = getCurrentContextName()
            if (name == current) {
                CONTEXT_NAME_HOLDER.set(oldState)
            } else {
                handleIncorrectContext(current)
            }
        }

        private void handleIncorrectContext(String currentContextName) {
            if (strict) {
                throw new IllegalStateException("Expected to be in context: $name but was: $currentContextName")
            }
            // otherwise: ignore silently
        }
    }
}
