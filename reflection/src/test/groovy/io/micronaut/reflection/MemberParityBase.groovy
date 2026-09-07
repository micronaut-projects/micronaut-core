package io.micronaut.reflection

/**
 * The super class of {@link MemberParityBean}: a property declared away from the introspected type, so that
 * the class a member reports as its declaring type can be compared between the two descriptions.
 */
class MemberParityBase {

    @Tag("inherited-field")
    private String note

    @Tag("inherited-getter")
    String getNote() {
        return note
    }

    void setNote(String note) {
        this.note = note
    }
}
