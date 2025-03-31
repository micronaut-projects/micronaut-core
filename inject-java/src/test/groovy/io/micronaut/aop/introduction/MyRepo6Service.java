package io.micronaut.aop.introduction;

import jakarta.inject.Singleton;

@Singleton
public class MyRepo6Service {

    private final DeleteByIdCrudRepo rawRepository;

    public MyRepo6Service(MyRepo6 myRepo6) {
        this.rawRepository = myRepo6;
    }

    void deleteById(Integer integer) {
        rawRepository.deleteById(integer);
    }
}
