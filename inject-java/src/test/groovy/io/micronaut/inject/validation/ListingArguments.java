package io.micronaut.inject.validation;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Optional;

@Introspected
public class ListingArguments {
    @PositiveOrZero
    private Integer offset = 0;

    public ListingArguments(Integer offset) {
        this.offset = offset;
    }


    @Nullable
    public Optional<Integer> getOffset() {
        return Optional.ofNullable(offset);
    }

    public void setOffset(@Nullable Integer offset) {
        this.offset = offset;
    }
}
