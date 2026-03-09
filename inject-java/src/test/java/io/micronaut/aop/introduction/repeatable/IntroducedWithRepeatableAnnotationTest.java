package io.micronaut.aop.introduction.repeatable;

import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.test.AbstractTypeElementTest;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class IntroducedWithRepeatableAnnotationTest extends AbstractTypeElementTest {

    @Test
    void testAnnotationIsRepeatableAfterAddedAnnotation() {
        var context = buildContext("""
            package test;

            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.ElementType;
            import java.lang.annotation.*;
            import io.micronaut.aop.Introduction;
            import io.micronaut.context.annotation.Type;
            import org.jspecify.annotations.NonNull;
            import jakarta.validation.Valid;
            import jakarta.validation.constraints.NotNull;
            import io.micronaut.aop.MethodInterceptor;
            import io.micronaut.aop.MethodInvocationContext;
            import org.jspecify.annotations.Nullable;
            import jakarta.inject.Singleton;
            import java.util.Optional;
            import java.lang.reflect.Method;
            import java.util.ArrayList;
            import java.util.List;

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.METHOD)
            @Documented
            @Inherited
            @interface MyDataMethod {
            }

            @Repeatable(MyRepContainer.class)
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.METHOD)
            @Documented
            @Inherited
            @interface MyRep {
                /**
                 * @return Name of the hint.
                 **/
                String name();

                /**
                 * @return Value of the hint.
                 **/
                String value();
            }

            @Documented
            @Retention(RetentionPolicy.RUNTIME)
            @Inherited
            @Target(ElementType.METHOD)
            @interface MyRepContainer {

                MyRep[] value();

            }

            @Introduction
            @Type(MyRepoIntroducer.class)
            @Documented
            @Retention(RetentionPolicy.RUNTIME)
            @Target({ElementType.TYPE})
            @Inherited
            @interface RepoDef {
            }


            @Singleton
            class MyRepoIntroducer implements MethodInterceptor<Object, Object> {

                @Nullable
                @Override
                public Object intercept(MethodInvocationContext<Object, Object> context) {
                    return null;
                }
            }

            interface CrudRepo<E, ID> {

                Optional<E> findById(ID id);

            }

            interface CrudRepoX<E, ID> extends CrudRepo<E, ID> {

                @MyRep(name = "aa", value = "vv")
                <S extends E> S saveAndFlush(@NonNull @Valid @NotNull S entity);
            }

            @RepoDef
            interface CustomCrudRepo3 extends CrudRepoX<String, Long> {

                @Override
                Optional<String> findById(Long aLong);
            }
            """);

        try {
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> annClass =
                (Class<? extends Annotation>) context.getClassLoader().loadClass("test.MyRep");
            Class<?> repoClass = context.getClassLoader().loadClass("test.CustomCrudRepo3");
            BeanDefinition<?> beanDef1 = context.getBeanDefinition(repoClass);
            var method = beanDef1.getRequiredMethod("saveAndFlush", String.class);

            assertTrue(beanDef1.getAnnotationMetadata().isRepeatableAnnotation(annClass));
            assertTrue(method.getAnnotationMetadata().isRepeatableAnnotation(annClass));
            assertTrue(method.getAnnotationMetadata().hasAnnotation(annClass));
        } catch (ClassNotFoundException e) {
            fail(e);
        } finally {
            context.close();
        }
    }
}
