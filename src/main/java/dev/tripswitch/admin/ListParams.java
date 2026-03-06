package dev.tripswitch.admin;

/** Common pagination parameters. */
public record ListParams(String cursor, int limit) {
    public ListParams(int limit) {
        this(null, limit);
    }

    public ListParams() {
        this(null, 0);
    }
}
