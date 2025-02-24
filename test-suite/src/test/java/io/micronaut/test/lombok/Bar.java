package io.micronaut.test.lombok;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.core.annotation.Introspected;

@Introspected
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(builder = Foo.FooBuilder.class)
public interface Bar {

    String getName();

}

