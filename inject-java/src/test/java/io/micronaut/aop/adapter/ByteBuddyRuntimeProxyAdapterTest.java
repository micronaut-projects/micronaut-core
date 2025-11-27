package io.micronaut.aop.adapter;

import io.micronaut.aop.introduction.MyRepoIntroducer;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ByteBuddyRuntimeProxyAdapterTest {

    @AfterEach
    void cleanup() {
        MyRepoIntroducer.EXECUTED_METHODS.clear();
    }

    @Test
    void test() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "RuntimeProxyAdapterTest"))) {
            ByteBuddyRuntimeAdapterBean repo = context.getBean(ByteBuddyRuntimeAdapterBean.class);
            MyAdapter myAdapter = context.getBean(MyAdapter.class);
            myAdapter.receive("Hello World");
            assertEquals("Hello World", repo.getMessage());
            assertTrue(repo.getClass().getName().contains("$ByteBuddyProxy"));
        }
    }
}
