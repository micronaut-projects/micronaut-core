package io.micronaut.test.lombok;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants
@Singleton
@Requires(property = "spec.name", value = FooBean.Fields.lombokFieldsTest)
public class FooBean {

    private String lombokFieldsTest;

}
