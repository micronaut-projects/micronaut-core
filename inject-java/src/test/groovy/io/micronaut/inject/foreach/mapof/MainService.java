package io.micronaut.inject.foreach.mapof;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "MapOfNameSpec")
@Singleton
public class MainService {

    private final MyFooService myFooService;
    private final MyServiceConsumer myServiceConsumer;

    public MainService(MyFooService myFooService, MyServiceConsumer myServiceConsumer) {
        this.myFooService = myFooService;
        this.myServiceConsumer = myServiceConsumer;
    }

    public MyFooService getMyFooService() {
        return myFooService;
    }

    public MyServiceConsumer getMyServiceConsumer() {
        return myServiceConsumer;
    }


}
