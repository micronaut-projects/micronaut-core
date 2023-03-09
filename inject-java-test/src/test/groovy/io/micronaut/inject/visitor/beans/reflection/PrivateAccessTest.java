package io.micronaut.inject.visitor.beans.reflection;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

public class PrivateAccessTest {
    public PrivateAccessTest() {
    }

    @Test
    public void optionalValueExtractor() {
        BeanIntrospection<OptionalHolder> introspection = BeanIntrospector.SHARED.getIntrospection(OptionalHolder.class);
        BeanProperty<OptionalHolder, Object> property = introspection.getProperty("optional").get();
        Assertions.assertNotNull(property.asArgument().getFirstTypeVariable().get().getAnnotationMetadata().getAnnotation(NotBlank.class));
        Assertions.assertEquals(property.getType(), Optional.class);
        Assertions.assertEquals(Optional.of("hello"), property.get(new OptionalHolder(Optional.of("hello"))));
    }

    @Test
    public void optionalIntValueExtractor() {
        BeanIntrospection<OptionalIntHolder> introspection = BeanIntrospector.SHARED.getIntrospection(OptionalIntHolder.class);
        BeanProperty<OptionalIntHolder, Object> property = introspection.getProperty("optionalInt").get();
        Assertions.assertNotNull(property.asArgument().getAnnotation(Min.class));
        Assertions.assertEquals(property.getType(), OptionalInt.class);
        Assertions.assertEquals(OptionalInt.of(123), property.get(new OptionalIntHolder(OptionalInt.of(123))));
    }

    @Test
    public void optionalLongValueExtractor() {
        BeanIntrospection<OptionalLongHolder> introspection = BeanIntrospector.SHARED.getIntrospection(OptionalLongHolder.class);
        BeanProperty<OptionalLongHolder, Object> property = introspection.getProperty("optionalLong").get();
        Assertions.assertNotNull(property.asArgument().getAnnotation(Min.class));
        Assertions.assertEquals(property.getType(), OptionalLong.class);
        Assertions.assertEquals(OptionalLong.of(123L), property.get(new OptionalLongHolder(OptionalLong.of(123L))));
    }

    @Test
    public void optionalDoubleValueExtractor() {
        BeanIntrospection<OptionalDoubleHolder> introspection = BeanIntrospector.SHARED.getIntrospection(OptionalDoubleHolder.class);
        BeanProperty<OptionalDoubleHolder, Object> property = introspection.getProperty("optionalDouble").get();
        Assertions.assertNotNull(property.asArgument().getAnnotation(DecimalMin.class));
        Assertions.assertEquals(property.getType(), OptionalDouble.class);
        Assertions.assertEquals(OptionalDouble.of(12.3), property.get(new OptionalDoubleHolder(OptionalDouble.of(12.3))));
    }

    @Test
    public void privateFieldWrite() {
        PrivateFieldBean bean = new PrivateFieldBean();
        BeanIntrospection<PrivateFieldBean> introspection = BeanIntrospector.SHARED.getIntrospection(PrivateFieldBean.class);
        BeanProperty<PrivateFieldBean, Object> property = introspection.getProperty("name").get();
        Assertions.assertNull(property.get(bean));
        property.set(bean, "hello");
        Assertions.assertEquals("hello", property.get(bean));
    }

    @Test
    public void privateFieldWrite2() {
        PrivateFieldBean2 bean = new PrivateFieldBean2();
        BeanIntrospection<PrivateFieldBean2> introspection = BeanIntrospector.SHARED.getIntrospection(PrivateFieldBean2.class);
        BeanProperty<PrivateFieldBean2, Object> property = introspection.getProperty("abc").get();
        Assertions.assertEquals(0, property.get(bean));
        property.set(bean, 123);
        Assertions.assertEquals(123, property.get(bean));
    }

    @Test
    public void privateMethodWrite() {
        PrivateMethodsBean bean = new PrivateMethodsBean();
        BeanIntrospection<PrivateMethodsBean> introspection = BeanIntrospector.SHARED.getIntrospection(PrivateMethodsBean.class);
        BeanProperty<PrivateMethodsBean, Object> property = introspection.getProperty("name").get();
        Assertions.assertNull(property.get(bean));
        property.set(bean, "hello");
        Assertions.assertEquals("hello", property.get(bean));
    }

}
