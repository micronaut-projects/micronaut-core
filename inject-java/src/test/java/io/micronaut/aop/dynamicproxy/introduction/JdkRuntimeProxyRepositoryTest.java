package io.micronaut.aop.dynamicproxy.introduction;

import io.micronaut.aop.introduction.MyRepoIntroducer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.ExecutableMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JdkRuntimeProxyRepositoryTest {

    @AfterEach
    void cleanup() {
        MyRepoIntroducer.EXECUTED_METHODS.clear();
    }

    @Test
    void test() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "JdkRuntimeProxyRepositoryTest"))) {
            JdkCustomCrudRepo repo = context.getBean(JdkCustomCrudRepo.class);
            assertNotNull(repo);

            assertNull(repo.findById(10L));
            assertEquals(1, MyRepoIntroducer.EXECUTED_METHODS.size());
            assertEquals("findById", MyRepoIntroducer.EXECUTED_METHODS.getFirst().getName());
            assertTrue(Proxy.isProxyClass(repo.getClass()), repo.getClass().getName());

            MyRepoIntroducer.EXECUTED_METHODS.clear();

            assertEquals("empty", repo.findByIdWithDefault(10L));
            assertEquals(1, MyRepoIntroducer.EXECUTED_METHODS.size());
        }
    }

    @Test
    void testMethodsCount() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "JdkRuntimeProxyRepositoryTest"))) {
            Collection<ExecutableMethod<JdkCustomCrudRepo, ?>> executableMethods = context.getBeanDefinition(JdkCustomCrudRepo.class).getExecutableMethods();
            assertEquals(1, executableMethods.size());
            assertEquals("findById", executableMethods.iterator().next().getMethodName());
        }
    }
}
