package io.micronaut.docs.aop.around;

// tag::imports[]
import io.micronaut.context.annotation.Type;
import io.micronaut.aop.Around;
import java.lang.annotation.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
// end::imports[]

// tag::annotation[]
@Documented
@Retention(RUNTIME) // <1>
@Target({ElementType.TYPE, ElementType.METHOD}) // <2>
@Around // <3>
@Type(NotNullInterceptor.class) // <4>
public @interface NotNull {
}
// end::annotation[]
