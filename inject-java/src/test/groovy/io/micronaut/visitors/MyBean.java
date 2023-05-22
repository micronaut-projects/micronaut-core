package io.micronaut.visitors;

import io.micronaut.context.annotation.Executable;

import java.util.List;

@jakarta.inject.Singleton
class MyBean {

    @Executable
    public void saveAll(List<@TypeUseRuntimeAnn Book> books) {
    }

    @Executable
    public <@TypeUseRuntimeAnn T extends Book> void saveAll2(List<? extends T> book) {
    }

    @Executable
    public <@TypeUseRuntimeAnn T extends Book> void saveAll3(List<T> book) {
    }

   @Executable
    public <T extends Book> void saveAll4(List<? extends T> book) {
    }

    @Executable
    public void save2(@TypeUseRuntimeAnn Book book) {
    }

    @Executable
    public <@TypeUseRuntimeAnn T extends Book> void save3(T book) {
    }

    @TypeUseRuntimeAnn
    @Executable
    public Book get() {
        return null;
    }
}
