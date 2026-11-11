package com.devisri.reposcout.match;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.devisri.reposcout.agent.AgentException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/** Step 3 of a job match: one AI call drafts resume bullets that may only point to evidence ids. */
@Service
public class BulletWriter {

    static final String SYSTEM_PROMPT = """
            You write resume bullet points for a candidate applying for: {jobTitle}.
            You are given the job's requirements and EVIDENCE: real code snippets from the candidate's GitHub
            repositories, each with an id like E1.

            Rules:
            - Write at most 8 bullets, most relevant to the job first.
            - Each bullet is one sentence, at most 30 words, starts with a strong past-tense verb, and says WHAT was
              built and WITH WHICH technology, e.g. "Built a REST API for a personal finance app with Spring Boot
              and PostgreSQL". Use the PROJECTS list to know what each repository is.
            - Prefer bullets about ONE specific project, described by what it does (e.g. "a personal finance
              assistant"), combining 2 or 3 technologies its evidence shows. Avoid vague phrases such as "across
              multiple repositories", "various endpoints" or "several projects".
            - Try to cover every requirement that has evidence, including testing and databases.
            - Do not mention file names, version numbers, dependency names or configuration details.
            - Each bullet must list 1 to 3 evidence ids that directly show the claim. Use only ids from the list.
            - Claim only what the evidence and project descriptions show. No numbers, users, performance or team
              claims unless shown.
            - Skip requirements whose evidence is weak; do not write a bullet without evidence.
            - requirement: the name of the requirement the bullet proves, exactly as given.
            """;

    /** One bullet as drafted by the AI, before checking. */
    public record DraftBullet(String text, String requirement, List<String> evidenceIds) {
    }

    public record Draft(List<DraftBullet> bullets) {
    }

    private final ChatClient chat;

    public BulletWriter(ChatClient.Builder chatBuilder) {
        this.chat = chatBuilder.build();
    }

    public Draft write(String jobTitle, Map<String, String> descriptions, List<Requirement> requirements,
                       List<Evidence> evidence) {
        try {
            Draft draft = chat.prompt()
                    .system(s -> s.text(SYSTEM_PROMPT).param("jobTitle", jobTitle))
                    .user(userMessage(descriptions, requirements, evidence))
                    .call()
                    .entity(Draft.class);
            return draft == null || draft.bullets() == null ? new Draft(List.of()) : draft;
        } catch (RuntimeException e) {
            throw AgentException.from(e, "The AI could not write bullets for this job. Please try again.");
        }
    }

    static String userMessage(Map<String, String> descriptions, List<Requirement> requirements, List<Evidence> evidence) {
        StringBuilder sb = new StringBuilder("PROJECTS:\n");
        evidence.stream().map(Evidence::repo).distinct().forEach(repo -> sb.append("- ").append(repo).append(": ")
                .append(descriptions.getOrDefault(repo.toLowerCase(Locale.ROOT), "(no description)")).append('\n'));
        sb.append("\nREQUIREMENTS:\n");
        requirements.forEach(r -> sb.append("- ").append(r.name()).append('\n'));
        sb.append("\nEVIDENCE:\n");
        for (Evidence e : evidence) {
            sb.append(e.id()).append(" (for ").append(e.requirement()).append(") ")
                    .append(e.repo()).append(" / ").append(e.path())
                    .append(" lines ").append(e.startLine()).append('-').append(e.endLine()).append(":\n")
                    .append(e.excerpt()).append("\n\n");
        }
        return sb.toString();
    }
}
