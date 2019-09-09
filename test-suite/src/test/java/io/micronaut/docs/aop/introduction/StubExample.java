package io.micronaut.docs.aop.introduction;

import java.time.LocalDateTime;

// tag::class[]
@Stub
public interface StubExample {

    @Stub("10")
    int getNumber();

    LocalDateTime getDate();
}
// end::class[]
