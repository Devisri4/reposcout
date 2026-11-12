package com.devisri.reposcout.match;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Step 4 of a job match, in plain Java: keeps only bullets backed by real evidence and works out the gaps.
 * The AI's evidence ids are checked against the list we gave it; nothing it says is trusted on its own.
 */
public final class MatchVerifier {

    static final int MAX_BULLETS = 8;
    static final int MAX_BULLET_LENGTH = 300;

    private MatchVerifier() {
    }

    public record Verified(List<MatchResult.RequirementStatus> requirements, List<MatchResult.Bullet> bullets,
                           List<MatchResult.DroppedClaim> dropped, List<String> gaps) {
    }

    public static Verified verify(BulletWriter.Draft draft, List<Requirement> requirements, List<Evidence> evidence) {
        Map<String, Evidence> byId = evidence.stream()
                .collect(Collectors.toMap(e -> e.id().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        Map<String, Requirement> reqByName = requirements.stream()
                .collect(Collectors.toMap(r -> r.name().toLowerCase(Locale.ROOT), Function.identity(), (a, b) -> a));

        List<MatchResult.Bullet> kept = new ArrayList<>();
        List<MatchResult.DroppedClaim> dropped = new ArrayList<>();
        Set<String> seenTexts = new HashSet<>();

        for (BulletWriter.DraftBullet b : draft.bullets() == null ? List.<BulletWriter.DraftBullet>of() : draft.bullets()) {
            if (b == null || b.text() == null || b.text().isBlank()) {
                continue;
            }
            String text = b.text().strip();
            if (!seenTexts.add(text.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (text.length() > MAX_BULLET_LENGTH) {
                dropped.add(new MatchResult.DroppedClaim(text, "too long to be a resume bullet"));
                continue;
            }
            Set<String> ids = new LinkedHashSet<>();
            List<String> given = b.evidenceIds() == null ? List.of() : b.evidenceIds();
            for (String id : given) {
                if (id != null && byId.containsKey(id.strip().toUpperCase(Locale.ROOT))) {
                    ids.add(id.strip().toUpperCase(Locale.ROOT));
                }
            }
            if (ids.isEmpty()) {
                dropped.add(new MatchResult.DroppedClaim(text, given.isEmpty()
                        ? "no evidence cited"
                        : "cited evidence that does not exist (" + String.join(", ", given) + ")"));
                continue;
            }
            if (kept.size() >= MAX_BULLETS) {
                dropped.add(new MatchResult.DroppedClaim(text, "more than " + MAX_BULLETS + " bullets"));
                continue;
            }
            // A bullet describes one project, so it may only cite code from that project's repository.
            List<Evidence> all = ids.stream().map(byId::get).toList();
            String repo = projectRepo(b.project(), evidence, all);
            List<Evidence> cited = all.stream().filter(e -> e.repo().equalsIgnoreCase(repo)).limit(3).toList();
            if (cited.isEmpty()) {
                String others = all.stream().map(e -> shortName(e.repo())).distinct().collect(Collectors.joining(", "));
                dropped.add(new MatchResult.DroppedClaim(text,
                        "describes " + shortName(repo) + " but cites code from " + others));
                continue;
            }
            // The requirement must be one we asked about; otherwise use the requirement of its first evidence.
            Requirement req = b.requirement() == null ? null : reqByName.get(b.requirement().strip().toLowerCase(Locale.ROOT));
            String requirement = req != null ? req.name() : cited.get(0).requirement();
            kept.add(new MatchResult.Bullet(text, requirement, cited));
        }

        Set<String> proven = new HashSet<>();
        kept.forEach(b -> {
            proven.add(b.requirement().toLowerCase(Locale.ROOT));
            b.evidence().forEach(e -> proven.add(e.requirement().toLowerCase(Locale.ROOT)));
        });

        List<MatchResult.RequirementStatus> statuses = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        for (Requirement r : requirements) {
            String key = r.name().toLowerCase(Locale.ROOT);
            int count = (int) evidence.stream().filter(e -> e.requirement().equalsIgnoreCase(r.name())).count();
            MatchResult.Status status = proven.contains(key) ? MatchResult.Status.PROVEN
                    : count > 0 ? MatchResult.Status.EVIDENCE_ONLY : MatchResult.Status.GAP;
            statuses.add(new MatchResult.RequirementStatus(r.name(), status, count));
            if (status == MatchResult.Status.GAP) {
                gaps.add(r.name());
            }
        }
        return new Verified(statuses, kept, dropped, gaps);
    }

    /** The repository the bullet names, if it is one we searched; otherwise the repository of its first evidence. */
    static String projectRepo(String project, List<Evidence> evidence, List<Evidence> cited) {
        if (project != null && !project.isBlank()) {
            String p = project.strip().toLowerCase(Locale.ROOT);
            for (Evidence e : evidence) {
                String repo = e.repo().toLowerCase(Locale.ROOT);
                if (repo.equals(p) || repo.endsWith("/" + p)) {
                    return e.repo();
                }
            }
        }
        return cited.get(0).repo();
    }

    static String shortName(String repo) {
        return repo.substring(repo.lastIndexOf('/') + 1);
    }
}
