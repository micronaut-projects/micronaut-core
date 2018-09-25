package io.micronaut.inject.qualifiers.compose;

@Composable
public class SecondThing implements Thing {
    @Override
    public int getNumber() {
        return 2;
    }
}

