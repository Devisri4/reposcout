package com.devisri.reposcout.store;

import java.util.Optional;
import java.util.regex.Pattern;

import com.devisri.reposcout.match.MatchResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Saves job-match results and loads them back for share links. */
@Service
public class MatchStore {

    private static final Logger log = LoggerFactory.getLogger(MatchStore.class);
    private static final Pattern UUID = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final SavedMatchRepository repository;
    private final ObjectMapper json;

    public MatchStore(SavedMatchRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    /**
     * Saves the result. A database problem is logged and reported as {@code false} instead of failing the match:
     * the user still sees their result, only the share link will not work.
     */
    public boolean save(MatchResult result) {
        try {
            repository.saveAndFlush(new SavedMatch(result.id(), result.githubUser(), truncate(result.jobTitle(), 200),
                    result.createdAt(), json.writeValueAsString(result)));
            return true;
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("Could not save job match {}: {}", result.id(), e.getMessage());
            return false;
        }
    }

    public Optional<MatchResult> find(String id) {
        if (id == null || !UUID.matcher(id).matches()) {
            return Optional.empty();
        }
        return repository.findById(id).map(saved -> {
            try {
                return json.readValue(saved.getResultJson(), MatchResult.class).withSaved(true);
            } catch (JsonProcessingException e) {
                log.error("Saved job match {} could not be read: {}", id, e.getMessage());
                return null;
            }
        });
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max);
    }
}
