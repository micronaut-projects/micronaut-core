package io.micronaut.aop.introduction;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Prototype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Java port of RepositoryScopeSpec.
 */
class RepositoryScopeTest {


    @Test
    void testDefaultRepositoryScopeIsPrototype() {
        try (ApplicationContext beanContext = ApplicationContext.run()) {

            MyPrototypeRepo instance1 = beanContext.getBean(MyPrototypeRepo.class);
            MyPrototypeRepo instance2 = beanContext.getBean(MyPrototypeRepo.class);

            assertNotSame(instance1, instance2);
        }
    }

    @Test
    void testNoMemoryLeak1() {
        try (ApplicationContext beanContext = ApplicationContext.run()) {
            MyPrototypeRepoIntroducer introducer = beanContext.getBean(MyPrototypeRepoIntroducer.class);
            introducer.methods.clear();
            introducer.repoMethods.clear();

            for (int i = 0; i < 1000; i++) {
                MyPrototypeRepo instance = beanContext.getBean(MyPrototypeRepo.class);
                instance.deleteById(111);
            }

            assertEquals(1, introducer.methods.size());
            assertEquals(1, introducer.repoMethods.size());
        }
    }

    @Test
    void testNoMemoryLeak2() {
        try (ApplicationContext beanContext = ApplicationContext.run()) {
            MyPrototypeRepoIntroducer introducer = beanContext.getBean(MyPrototypeRepoIntroducer.class);
            introducer.methods.clear();
            introducer.repoMethods.clear();

            MyPrototypeRepo instance = beanContext.getBean(MyPrototypeRepo.class);
            for (int i = 0; i < 1000; i++) {
                instance.deleteById(123);
            }

            assertEquals(1, introducer.methods.size());
            assertEquals(1, introducer.repoMethods.size());
        }
    }

    @Test
    void testNoMemoryLeak3() {
        try (ApplicationContext beanContext = ApplicationContext.run()) {
            MyPrototypeRepoIntroducer introducer = beanContext.getBean(MyPrototypeRepoIntroducer.class);
            introducer.methods.clear();
            introducer.repoMethods.clear();

            MyPrototypeService myService = beanContext.getBean(MyPrototypeService.class);
            for (int i = 0; i < 1000; i++) {
                myService.myPrototypeRepo.deleteById(123);
            }

            assertEquals(1, introducer.methods.size());
            assertEquals(1, introducer.repoMethods.size());
        }
    }

    @Prototype
    static class MyPrototypeService {
        final MyPrototypeRepo myPrototypeRepo;

        MyPrototypeService(MyPrototypeRepo myPrototypeRepo) {
            this.myPrototypeRepo = myPrototypeRepo;
        }
    }
}
