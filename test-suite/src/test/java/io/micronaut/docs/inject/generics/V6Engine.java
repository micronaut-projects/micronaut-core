package io.micronaut.docs.inject.generics;

import javax.inject.Singleton;

// tag::class[]
@Singleton
public class V6Engine implements Engine<V6> {  // <1>
    @Override
    public V6 getCylinderProvider() {
        return new V6();
    }
}
// end::class[]
