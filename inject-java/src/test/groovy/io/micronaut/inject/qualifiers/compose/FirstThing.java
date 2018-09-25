package io.micronaut.inject.qualifiers.compose;

@Composable
public class FirstThing implements Thing {
    @Override
    public int getNumber() {
        return 1;
    }
}
