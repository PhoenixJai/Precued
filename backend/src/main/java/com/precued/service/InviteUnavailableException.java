package com.precued.service;

/**
 * A token is known but no longer usable (expired or fully consumed). Services
 * use a targeted no-rollback rule for this exception so persisting the
 * terminal Invite status is not undone merely because the request is then
 * rejected. Other business-rule failures still roll back normally.
 */
public class InviteUnavailableException extends IllegalStateException {
    public InviteUnavailableException(String message) {
        super(message);
    }
}
