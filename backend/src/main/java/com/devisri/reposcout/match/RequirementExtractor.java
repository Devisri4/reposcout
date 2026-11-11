package com.devisri.reposcout.match;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.devisri.reposcout.agent.AgentException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** Step 1 of a job match: one AI call turns a job description into a short list of checkable requirements. */
@Service
public class RequirementExtractor {

    static final int MAX_REQUIREMENTS = 10;
    static final int MAX_TERMS = 6;

    static final String SYSTEM_PROMPT = """
            You read job descriptions for software roles and list the technical requirements a candidate's
            code could prove. Return at most 10 requirements, most important first.

            For each requirement give:
            - name: a short skill name, e.g. "Spring Boot", "REST APIs", "React", "Unit testing", "Docker"
            - searchTerms: 2 to 6 exact words or snippets that appear in source code or build files of a project
              that uses this skill, e.g. for Spring Boot: "@SpringBootApplication", "spring-boot-starter";
              for React: "useState", "react-dom"; for Docker: "FROM ", "docker-compose".

            Skip soft skills (communication, teamwork), years of experience, degrees and benefits.
            Also give jobTitle: the role title, or "Software role" if none is given.
            """;

    /** What the AI returns; also the cleaned result. */
    public record JobRequirements(String jobTitle, List<Requirement> requirements) {
    }

    private final ChatClient chat;

    public RequirementExtractor(ChatClient.Builder chatBuilder) {
        this.chat = chatBuilder.build();
    }

    public JobRequirements extract(String jobDescription) {
        JobRequirements raw;
        try {
            raw = chat.prompt().system(SYSTEM_PROMPT).user(jobDescription).call().entity(JobRequirements.class);
        } catch (RuntimeException e) {
            throw AgentException.from(e, "The AI could not read this job description. Please try again.");
        }
        JobRequirements cleaned = clean(raw);
        if (cleaned.requirements().isEmpty()) {
            throw new AgentException("No technical requirements were found in this job description.", false, null);
        }
        return cleaned;
    }

    /** Trims, de-duplicates and caps what the model returned; never trusts its sizes or formatting. */
    static JobRequirements clean(JobRequirements raw) {
        if (raw == null || raw.requirements() == null) {
            return new JobRequirements("Software role", List.of());
        }
        Map<String, Requirement> byName = new LinkedHashMap<>();
        for (Requirement r : raw.requirements()) {
            if (r == null || r.name() == null || r.name().isBlank() || byName.size() >= MAX_REQUIREMENTS) {
                continue;
            }
            String name = r.name().strip();
            String key = name.toLowerCase(Locale.ROOT);
            if (byName.containsKey(key)) {
                continue;
            }
            Set<String> terms = new LinkedHashSet<>();
            List<String> given = r.searchTerms() == null ? List.of() : r.searchTerms();
            for (String t : given) {
                if (t != null && t.strip().length() >= 2 && t.strip().length() <= 60 && terms.size() < MAX_TERMS) {
                    terms.add(t.strip());
                }
            }
            if (terms.size() < MAX_TERMS && name.length() <= 60) {
                terms.add(name); // the name itself is often a good search term ("Docker", "React")
            }
            byName.put(key, new Requirement(name, new ArrayList<>(terms)));
        }
        String title = raw.jobTitle() == null || raw.jobTitle().isBlank() ? "Software role" : raw.jobTitle().strip();
        return new JobRequirements(title.length() > 120 ? title.substring(0, 120) : title, List.copyOf(byName.values()));
    }
}
