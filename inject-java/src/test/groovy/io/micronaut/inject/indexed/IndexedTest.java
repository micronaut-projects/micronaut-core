package io.micronaut.inject.indexed;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class IndexedTest {

    @Test
    void test() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "IndexedTest"))) {
            BeanDefinition<MyBean> beanDefinition = context.getBeanDefinition(MyBean.class);
            BeanDefinitionReference<MyBean> beanDefinitionReference = (BeanDefinitionReference<MyBean>) beanDefinition;
            Assertions.assertEquals(List.of(MySupplier.class), List.of(beanDefinitionReference.getIndexes()));
        }
    }

}

@Requires(property = "spec.name", value = "IndexedTest")
@Singleton
@Indexed(MySupplier.class)
class MyBean implements MySupplier {

    @Override
    public String get() {
        return "foobar";
    }
}

interface MySupplier {

    String get();

}
