package com.devisri.reposcout.match;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import com.devisri.reposcout.agent.AgentException;
import com.devisri.reposcout.agent.AgentProperties;
import com.devisri.reposcout.repo.RepoLoadException;
import com.devisri.reposcout.store.MatchStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs a job match: requirements (AI) -> evidence from the user's repos (Java) -> bullets (AI) -> checks (Java).
 * Two AI calls per match, so it fits the free Groq tier.
 */
@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);
    private static final Pattern GITHUB_USER = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$");

    private final RequirementExtractor extractor;
    private final EvidenceFinder finder;
    private final BulletWriter writer;
    private final AgentProperties agentProps;
    private final MatchStore store;

    public MatchService(RequirementExtractor extractor, EvidenceFinder finder, BulletWriter writer,
                        AgentProperties agentProps, MatchStore store) {
        this.store = store;
        this.extractor = extractor;
        this.finder = finder;
        this.writer = writer;
        this.agentProps = agentProps;
    }

    public MatchResult match(String githubUser, String jobDescription) {
        String user = cleanUser(githubUser);
        if (!agentProps.hasApiKey()) {
            throw new AgentException(
                    "The AI key is not configured on the server (set the GROQ_API_KEY environment variable).", false, null);
        }
        long started = System.currentTimeMillis();

        RequirementExtractor.JobRequirements job = extractor.extract(jobDescription.strip());
        EvidenceFinder.Result found = finder.find(user, job.requirements());
        BulletWriter.Draft draft = found.evidence().isEmpty()
                ? new BulletWriter.Draft(List.of()) // nothing to cite: skip the second AI call
                : writer.write(job.jobTitle(), found.descriptions(), job.requirements(), found.evidence());
        MatchVerifier.Verified checked = MatchVerifier.verify(draft, job.requirements(), found.evidence());

        log.info("Job match for {}: {} requirements, {} repos, {} evidence, {} bullets kept, {} dropped, {} ms",
                user, job.requirements().size(), found.reposScanned().size(), found.evidence().size(),
                checked.bullets().size(), checked.dropped().size(), System.currentTimeMillis() - started);

        MatchResult result = new MatchResult(UUID.randomUUID().toString(), user, job.jobTitle(), Instant.now(),
                found.reposScanned(), found.reposSkipped(), checked.requirements(), checked.bullets(),
                checked.dropped(), checked.gaps(), false);
        return result.withSaved(store.save(result));
    }

    /** Accepts "devi", "@devi" or "https://github.com/devi"; GitHub usernames are letters, digits and dashes. */
    static String cleanUser(String input) {
        String u = input == null ? "" : input.strip();
        u = u.replaceFirst("^(?:https?://)?(?:www\\.)?github\\.com/", "").replaceFirst("^@", "");
        u = u.replaceAll("/+$", "");
        if (!GITHUB_USER.matcher(u).matches()) {
            throw new RepoLoadException("That doesn't look like a GitHub username, e.g. Devisri4 or https://github.com/Devisri4");
        }
        return u;
    }
}
