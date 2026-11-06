package com.devisri.reposcout.repo;

/**
 * A repo could not be loaded. The message is safe and meant to be shown to the user.
 */
public class RepoLoadException extends RuntimeException {

    public RepoLoadException(String message) {
        super(message);
    }

    public RepoLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
