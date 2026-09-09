package com.enterprise.licensing.engine.user;

public record NamedSeatOverride(
        String type,
        String actor,
        String justification,
        String previousUser,
        String replacementUser) {

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    public boolean completeFor(String releasedUser) {
        return ("EMPLOYEE_DEPARTURE".equals(type) || "ASSIGNMENT_CORRECTION".equals(type))
                && present(actor)
                && present(justification)
                && present(previousUser)
                && present(replacementUser)
                && previousUser.equals(releasedUser)
                && !previousUser.equals(replacementUser);
    }
}
