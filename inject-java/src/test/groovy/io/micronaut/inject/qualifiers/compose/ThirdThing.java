package io.micronaut.inject.qualifiers.compose;

import javax.inject.Singleton;

@Singleton
public class ThirdThing implements Thing {
    @Override
    public int getNumber() {
        return 3;
    }
}
