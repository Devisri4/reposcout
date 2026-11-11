package com.devisri.reposcout.match;

import java.time.Instant;
import java.util.List;

/** Everything the job-match page shows. Saved as JSON so a result can be shared by link. */
public record MatchResult(
        String id,
        String githubUser,
        String jobTitle,
        Instant createdAt,
        List<String> reposScanned,
        List<String> reposSkipped,
        List<RequirementStatus> requirements,
        List<Bullet> bullets,
        List<DroppedClaim> dropped,
        List<String> gaps,
        boolean saved) {

    public enum Status { PROVEN, EVIDENCE_ONLY, GAP }

    /** How well one requirement is covered. */
    public record RequirementStatus(String name, Status status, int evidenceCount) {
    }

    /** A resume bullet whose every citation is real evidence. */
    public record Bullet(String text, String requirement, List<Evidence> evidence) {
    }

    /** A bullet the AI wrote that was removed, and why. */
    public record DroppedClaim(String text, String reason) {
    }

    /** The same result, marked as saved (it can be opened again with its share link) or not. */
    public MatchResult withSaved(boolean isSaved) {
        return new MatchResult(id, githubUser, jobTitle, createdAt, reposScanned, reposSkipped, requirements, bullets,
                dropped, gaps, isSaved);
    }
}
