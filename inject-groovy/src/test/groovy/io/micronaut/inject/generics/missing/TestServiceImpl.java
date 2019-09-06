package io.micronaut.inject.generics.missing;

import io.reactivex.Flowable;

import javax.inject.Singleton;
import java.util.List;

@Singleton
public class TestServiceImpl implements TestService {

    @Override
    public <T extends ListArguments> Flowable<List<String>> findAll(T args) {
        return Flowable.empty();
    }
}
