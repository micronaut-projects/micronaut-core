package io.micronaut.aop.named;

import io.micronaut.aop.Intercepted;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of NamedAopAdviceSpec.
 */
class NamedAopAdviceTest {

    @Test
    void namedBeansWithAop_primaryIncluded() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "aop.test.named.default", 0,
            "aop.test.named.one", 1,
            "aop.test.named.two", 2
        ))) {
            NamedInterface defaultBean = context.getBean(NamedInterface.class);
            assertInstanceOf(Intercepted.class, defaultBean);
            assertEquals("default", defaultBean.doStuff());

            assertEquals("one", context.getBean(NamedInterface.class, Qualifiers.byName("one")).doStuff());
            assertEquals("two", context.getBean(NamedInterface.class, Qualifiers.byName("two")).doStuff());

            Collection<NamedInterface> all = context.getBeansOfType(NamedInterface.class);
            assertEquals(3, all.size());
            assertTrue(all.stream().allMatch(Intercepted.class::isInstance));
        }
    }

    @Test
    void namedBeansWithAop_noPrimary() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "aop.test.named.one", 1,
            "aop.test.named.two", 2
        ))) {
            assertEquals("one", context.getBean(NamedInterface.class, Qualifiers.byName("one")).doStuff());
            assertEquals("two", context.getBean(NamedInterface.class, Qualifiers.byName("two")).doStuff());

            Collection<NamedInterface> all = context.getBeansOfType(NamedInterface.class);
            assertEquals(2, all.size());
            assertTrue(all.stream().allMatch(Intercepted.class::isInstance));
        }
    }

    @Test
    void manuallyNamedBeansWithAop() {
        try (ApplicationContext context = ApplicationContext.run()) {
            OtherInterface first = context.getBean(OtherInterface.class, Qualifiers.byName("first"));
            assertEquals("first", first.doStuff());

            OtherInterface second = context.getBean(OtherInterface.class, Qualifiers.byName("second"));
            assertEquals("second", second.doStuff());

            Collection<OtherInterface> all = context.getBeansOfType(OtherInterface.class);
            assertEquals(2, all.size());
            assertTrue(all.stream().allMatch(Intercepted.class::isInstance));

            // OtherBean injection of named beans
            OtherBean otherBean = context.getBean(OtherBean.class);
            assertEquals("first", otherBean.first.doStuff());
            assertEquals("second", otherBean.second.doStuff());
        }
    }

    @Test
    void namedBeanRelyingOnNonIterableConfig() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("other.interfaces.third", "third"))) {
            OtherInterface third = context.getBean(OtherInterface.class, Qualifiers.byName("third"));
            assertEquals("third", third.doStuff());
        }
    }
}
