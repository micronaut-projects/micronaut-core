package io.micronaut.aop.named;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
public class OtherBean {

    @Inject @Named("first") public OtherInterface first;
    @Inject @Named("second") public OtherInterface second;
}
