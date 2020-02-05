package io.micronaut.docs.server.binding;

import io.micronaut.core.annotation.Introspected;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

@Introspected
public class PaginationCommand {

    @PositiveOrZero
    @Nullable
    private Integer offset;

    @Positive
    @Nullable
    private Integer max;

    @Nullable
    @Pattern(regexp = "name|href|title")
    private String sort;

    @Nullable
    @Pattern(regexp = "asc|desc|ASC|DESC")
    private String order;

    @Nullable
    public Integer getOffset() {
        return offset;
    }

    public void setOffset(@Nullable Integer offset) {
        this.offset = offset;
    }

    @Nullable
    public Integer getMax() {
        return max;
    }

    public void setMax(@Nullable Integer max) {
        this.max = max;
    }

    @Nullable
    public String getSort() {
        return sort;
    }

    public void setSort(@Nullable String sort) {
        this.sort = sort;
    }

    @Nullable
    public String getOrder() {
        return order;
    }

    public void setOrder(@Nullable String order) {
        this.order = order;
    }
}
