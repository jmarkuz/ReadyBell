package org.readybell.session.domain;

/**
 * Lifecycle status of a {@link CustomerSession}.
 *
 * <ul>
 *   <li>{@link #ACTIVE} – the session has been created and is currently open.</li>
 *   <li>{@link #ENDED} – the session has been closed and accepts no further activity.</li>
 * </ul>
 */
public enum SessionStatus {
    ACTIVE,
    ENDED
}
