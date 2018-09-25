package io.micronaut.inject.qualifiers.compose;

import javax.inject.Inject;
import java.util.stream.Stream;

@Composes(Thing.class)
public class CompositeThing implements Thing {

    private final Thing[] things;

    @Inject
    public CompositeThing(@Composable Thing[] things) {
        this.things = things;
    }

    @Override
    public int getNumber() {
        return Stream
                .of(things)
                .mapToInt(Thing::getNumber)
                .sum();
    }

}
