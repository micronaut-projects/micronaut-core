package io.micronaut.core.annotation.beans;

import io.micronaut.core.annotation.beans.introduction.Stub;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;

@Stub
public interface TestIntroduction {

    @Produces(MediaType.APPLICATION_JSON)
    String testMethod();
}
