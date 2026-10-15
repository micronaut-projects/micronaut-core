package io.micronaut.inject.records;

/**
 * A record whose accessors implement interface methods declared with a supertype return type,
 * so javac emits synthetic bridge accessors next to the real ones.
 */
public record CovariantRecord(String id, CovariantRecord.Details details)
    implements CovariantIdentified<String>, CovariantHasDetails {

    public interface View {
    }

    public record Details(String value) implements View {
    }
}
