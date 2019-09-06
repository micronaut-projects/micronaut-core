package io.micronaut.inject.generics.missing;


import io.reactivex.Flowable;

import java.util.List;

public interface TestService {

    <T extends ListArguments> Flowable<List<String>> findAll(T args);

}
