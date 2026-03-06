package dev.tripswitch.admin;

import dev.tripswitch.TripSwitchException;

import java.util.Iterator;

/**
 * Iterator-like interface for paginated results.
 * Call {@link #hasNext()} to fetch the next page if needed,
 * then {@link #next()} to get each item.
 * Check {@link #getError()} after iteration completes.
 *
 * @param <T> the item type
 */
public interface Pager<T> extends Iterator<T> {

    /** Returns any error that occurred during iteration. */
    TripSwitchException getError();
}
