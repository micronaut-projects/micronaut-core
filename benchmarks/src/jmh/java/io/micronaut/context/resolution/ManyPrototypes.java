package io.micronaut.context.resolution;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;

/**
 * Enough distinct prototype types to exceed the 30-entry candidate caches.
 */
final class ManyPrototypes {
    static final Class<?>[] TYPES = {
        P00.class,
        P01.class,
        P02.class,
        P03.class,
        P04.class,
        P05.class,
        P06.class,
        P07.class,
        P08.class,
        P09.class,
        P10.class,
        P11.class,
        P12.class,
        P13.class,
        P14.class,
        P15.class,
        P16.class,
        P17.class,
        P18.class,
        P19.class,
        P20.class,
        P21.class,
        P22.class,
        P23.class,
        P24.class,
        P25.class,
        P26.class,
        P27.class,
        P28.class,
        P29.class,
        P30.class,
        P31.class,
        P32.class,
        P33.class,
        P34.class,
        P35.class,
        P36.class,
        P37.class,
        P38.class,
        P39.class,
        P40.class,
        P41.class,
        P42.class,
        P43.class,
        P44.class,
        P45.class,
        P46.class,
        P47.class
    };

    private ManyPrototypes() {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P00 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P01 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P02 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P03 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P04 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P05 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P06 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P07 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P08 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P09 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P10 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P11 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P12 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P13 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P14 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P15 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P16 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P17 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P18 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P19 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P20 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P21 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P22 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P23 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P24 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P25 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P26 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P27 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P28 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P29 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P30 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P31 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P32 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P33 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P34 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P35 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P36 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P37 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P38 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P39 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P40 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P41 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P42 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P43 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P44 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P45 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P46 {
    }

    @Prototype
    @Requires(property = "spec.name", value = Fixtures.SPEC)
    static class P47 {
    }
}
