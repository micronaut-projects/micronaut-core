package pythontest.introduction.reflective;

import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Type;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An introduction whose interceptor builds the implementation reflectively from the
 * Java interface, the way LangChain4j's {@code AiServices.builder(Class)} does.
 */
@Introduction
@Type(ReflectiveServiceInterceptor.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReflectiveService {
    String value() default "";
}
