package io.micronaut.docs.server.binding;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;

import java.util.Objects;

@ReflectiveAccess
@Introspected
public class Point {
    private Integer x;
    private Integer y;

    public Point(
        @JsonProperty("x") Integer x, // @JsonProperty for jackson databind
        @JsonProperty("y") Integer y // @JsonProperty for jackson databind
    ) {
        this.x = x;
        this.y = y;
    }

    public Integer getX() {
        return x;
    }

    public void setX(Integer x) {
        this.x = x;
    }

    public Integer getY() {
        return y;
    }

    public void setY(Integer y) {
        this.y = y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Point point = (Point) o;

        if (!Objects.equals(x, point.x)) {
            return false;
        }
        return Objects.equals(y, point.y);
    }

    @Override
    public int hashCode() {
        int result = x != null ? x.hashCode() : 0;
        result = 31 * result + (y != null ? y.hashCode() : 0);
        return result;
    }
}
