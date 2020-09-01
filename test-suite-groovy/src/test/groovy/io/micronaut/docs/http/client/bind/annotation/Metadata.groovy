package io.micronaut.docs.http.client.bind.annotation;

//tag::clazz[]
import io.micronaut.core.bind.annotation.Bindable
import java.lang.annotation.*

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@Bindable
@interface Metadata {

}
//end::clazz[]
