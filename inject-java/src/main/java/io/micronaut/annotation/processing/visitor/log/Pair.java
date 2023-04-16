package io.micronaut.annotation.processing.visitor.log;

import java.util.Objects;

class Pair<A, B> {

    public final A fst;
    public final B snd;

    Pair(A fst, B snd) {
        this.fst = fst;
        this.snd = snd;
    }

    @Override
    public String toString() {
        return "Pair[" + fst + "," + snd + "]";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Pair<?, ?> pair &&
            Objects.equals(fst, pair.fst) &&
            Objects.equals(snd, pair.snd);
    }

    @Override
    public int hashCode() {
        if (fst == null) {
            return (snd == null) ? 0 : snd.hashCode() + 1;
        } else if (snd == null) {
            return fst.hashCode() + 2;
        } else {
            return fst.hashCode() * 17 + snd.hashCode();
        }
    }

    public static <A, B> Pair<A, B> of(A a, B b) {
        return new Pair<>(a, b);
    }
}
