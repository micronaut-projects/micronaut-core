package io.micronaut.core.annotation.beans;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.PropertySource;

import javax.inject.Singleton;

@Singleton
@PropertySource({
        @Property(name = "a", value = "1"),
        @Property(name = "b", value = "2"),
        @Property(name = "c", value = "3"),
        @Property(name = "d", value = "4"),
        @Property(name = "e", value = "5")/*,
        @Property(name = "f", value = "6"),
        @Property(name = "g", value = "7"),
        @Property(name = "h", value = "8"),
        @Property(name = "i", value = "9"),
        @Property(name = "j", value = "10"),
        @Property(name = "k", value = "11"),
        @Property(name = "l", value = "12"),
        @Property(name = "m", value = "13"),
        @Property(name = "n", value = "14"),
        @Property(name = "o", value = "15"),
        @Property(name = "p", value = "16"),
        @Property(name = "q", value = "17"),
        @Property(name = "r", value = "18"),
        @Property(name = "s", value = "19"),
        @Property(name = "t", value = "20"),
        @Property(name = "u", value = "21"),
        @Property(name = "v", value = "22"),
        @Property(name = "w", value = "23"),
        @Property(name = "x", value = "24"),
        @Property(name = "y", value = "25"),
        @Property(name = "z", value = "26")*/
})
public class NestedProperties {
}
