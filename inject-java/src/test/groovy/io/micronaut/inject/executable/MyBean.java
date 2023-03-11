package io.micronaut.inject.executable;

import io.micronaut.context.annotation.Executable;

import jakarta.validation.Valid;
import java.util.List;

@jakarta.inject.Singleton
class MyBean {

    @Executable
    public void saveAll(@Valid List<@TypeUseRuntimeAnn Book> books) {
    }

    @Executable
    public <T extends io.micronaut.inject.executable.Book> void saveAll2(@Valid List<? extends T> book) {
    }

    @Executable
    public <T extends io.micronaut.inject.executable.Book> void saveAll3(@Valid List<T> book) {
    }

    @Executable
    public void save2(@Valid io.micronaut.inject.executable.Book book) {
    }

    @Executable
    public <T extends io.micronaut.inject.executable.Book> void save3(@Valid T book) {
    }

    @Executable
    public io.micronaut.inject.executable.Book get() {
        return null;
    }
}
