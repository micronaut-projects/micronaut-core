package io.micronaut.test.lombok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

public class LombokIntrospectedBuilderTest {

    @Test
    void testNoArgsAndAllArgsConstructor() {
        BeanIntrospection<BarCommand> introspection = BeanIntrospection.getIntrospection(BarCommand.class);

        BeanIntrospection.Builder<BarCommand> builder = introspection.builder();

        builder.with("foo", "one");
        builder.with("bar", "two");
        builder.with("baz", "three");
        BarCommand result = builder.build();
        assertEquals("BarCommand(foo=one, bar=two, baz=three)", result.toString());
    }

    @Test
    void testLombokNoArgsConstructor() {
        BeanIntrospection<Book> introspection = BeanIntrospection.getIntrospection(Book.class);
        assertEquals(0, introspection.getConstructorArguments().length);
        Book book = introspection.instantiate();
        assertNotNull(book);
    }

    @Test
    void testLombokRecordBuilder() {
        BeanIntrospection<RobotRecord> introspection = BeanIntrospection.getIntrospection(RobotRecord.class);

        assertTrue(introspection.hasBuilder());
        BeanProperty<RobotRecord, String> property =
            introspection.getRequiredProperty("name", String.class);

        assertTrue(property.hasSetterOrConstructorArgument());
        BeanConstructor<RobotRecord> constructor = introspection.getConstructor();
        assertNotNull(constructor);
        assertEquals(1, constructor.getArguments().length);

        RobotRecord robot = introspection.instantiate("test");
        assertEquals("test", robot.name());
    }

    @Test
    void testLombokBuilder() {
        BeanIntrospection.Builder<RobotEntity> builder = BeanIntrospection.getIntrospection(RobotEntity.class)
            .builder();

        RobotEntity robotEntity = builder.with("name", "foo")
            .build();

        assertEquals("foo", robotEntity.getName());
    }

    @Test
    void testLombokBuilder2() {
        BeanIntrospection.Builder<MyEntity> builder = BeanIntrospection.getIntrospection(MyEntity.class)
            .builder();
        MyEntity.MyEntityBuilder builder1 = MyEntity.builder();
        builder.with("name", "foo");
        builder.with("id", "123");
        MyEntity myEntity = builder.build();
        assertEquals("foo", myEntity.getName());
        assertEquals("123", myEntity.getId());
    }

    @Test
    void testLombokBuilderWithInnerClasses() {
        BeanIntrospection.Builder<SimpleEntity> builder = BeanIntrospection.getIntrospection(SimpleEntity.class)
            .builder();

        String id = UUID.randomUUID().toString();
        SimpleEntity simpleEntity = builder.with("id", id)
            .build();

        assertEquals(id, simpleEntity.getId());

        BeanIntrospection<SimpleEntity.CompartmentCreationTimeIndexPrefix> innerClassIntrospection =
            BeanIntrospection.getIntrospection(SimpleEntity.CompartmentCreationTimeIndexPrefix.class);
        Assertions.assertNotNull(innerClassIntrospection);

        BeanIntrospection.Builder<SimpleEntity.CompartmentCreationTimeIndexPrefix> innerClassBuilder =
            innerClassIntrospection.builder();

        long current = Instant.now().toEpochMilli();
        SimpleEntity.CompartmentCreationTimeIndexPrefix innerClassEntity =
            innerClassBuilder.with("compartmentId", "c1").with("timeCreated", current).build();

        assertEquals("c1", innerClassEntity.getCompartmentId());
        assertEquals(current, innerClassEntity.getTimeCreated());
    }
}
