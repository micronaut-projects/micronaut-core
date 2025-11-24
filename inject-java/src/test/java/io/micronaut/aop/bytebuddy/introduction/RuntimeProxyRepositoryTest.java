package io.micronaut.aop.bytebuddy.introduction;

import io.micronaut.aop.introduction.MyRepoIntroducer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.ExecutableMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeProxyRepositoryTest {

    @AfterEach
    void cleanup() {
        MyRepoIntroducer.EXECUTED_METHODS.clear();
    }

    @Test
    void test() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "RuntimeProxyTest"))) {
            CustomCrudRepo repo = context.getBean(CustomCrudRepo.class);
            assertNotNull(repo);

            assertNull(repo.findById(10L));
            assertEquals(1, MyRepoIntroducer.EXECUTED_METHODS.size());
            assertEquals("findById", MyRepoIntroducer.EXECUTED_METHODS.getFirst().getName());
            assertTrue(repo.getClass().getName().contains("ByteBuddyProxy"), repo.getClass().getName());

            MyRepoIntroducer.EXECUTED_METHODS.clear();

            assertEquals("empty", repo.findByIdWithDefault(10L));
            assertEquals(1, MyRepoIntroducer.EXECUTED_METHODS.size());
        }
    }

    @Test
    void testMethodsCount() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "RuntimeProxyTest"))) {
            Collection<ExecutableMethod<CustomCrudRepo, ?>> executableMethods = context.getBeanDefinition(CustomCrudRepo.class).getExecutableMethods();
            assertEquals(1, executableMethods.size());
        }
    }
}
