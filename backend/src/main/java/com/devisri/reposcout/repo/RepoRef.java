package com.devisri.reposcout.repo;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A GitHub repository named by owner and repo, parsed from whatever the user pasted.
 */
public record RepoRef(String owner, String name) {

    // Optional scheme and host, then owner/name, then anything (like /tree/main/src) we ignore.
    private static final Pattern REPO = Pattern.compile(
            "^(?:(?:https?://)?(?:www\\.)?github\\.com/)?([A-Za-z0-9-]+)/([A-Za-z0-9._-]+?)(?:\\.git)?(?:[/?#].*)?$");

    /**
     * Accepts {@code https://github.com/owner/repo}, {@code github.com/owner/repo/tree/main/src},
     * {@code owner/repo.git}, or {@code owner/repo}.
     *
     * @throws RepoLoadException if the text is not a GitHub repository link
     */
    public static RepoRef parse(String input) {
        if (input == null || input.isBlank()) {
            throw new RepoLoadException("Please paste a GitHub repository link.");
        }
        Matcher m = REPO.matcher(input.trim());
        if (!m.matches() || m.group(2).equals(".") || m.group(2).equals("..")) {
            throw new RepoLoadException(
                    "That doesn't look like a GitHub repository link. Expected https://github.com/owner/repo");
        }
        return new RepoRef(m.group(1), m.group(2));
    }

    /** Case-insensitive key, because GitHub treats Owner/Repo and owner/repo as the same repo. */
    public String cacheKey() {
        return (owner + "/" + name).toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return owner + "/" + name;
    }
}
