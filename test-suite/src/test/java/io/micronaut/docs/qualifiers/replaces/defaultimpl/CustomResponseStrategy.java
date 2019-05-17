package io.micronaut.docs.qualifiers.replaces.defaultimpl;

//tag::clazz[]
import io.micronaut.context.annotation.Replaces;
import javax.inject.Singleton;

@Singleton
@Replaces(ResponseStrategy.class)
public class CustomResponseStrategy implements ResponseStrategy {

}
//end::clazz[]