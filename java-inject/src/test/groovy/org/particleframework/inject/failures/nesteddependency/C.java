package org.particleframework.inject.failures.nesteddependency;

import javax.inject.Singleton;

@Singleton
public class C {
    public C(D d) {

    }
}
