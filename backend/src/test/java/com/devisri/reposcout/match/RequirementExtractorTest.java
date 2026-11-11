package com.devisri.reposcout.match;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import com.devisri.reposcout.agent.AgentException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.TransientAiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequirementExtractorTest {

    private static RequirementExtractor extractor(FakeChatModel model) {
        return new RequirementExtractor(ChatClient.builder(model));
    }

    @Test
    void turnsTheModelsJsonIntoRequirements() {
        FakeChatModel model = new FakeChatModel().reply("""
                {"jobTitle":"Java Full Stack Developer","requirements":[
                  {"name":"Spring Boot","searchTerms":["@SpringBootApplication","spring-boot-starter"]},
                  {"name":"React","searchTerms":["useState","react-dom"]}]}
                """);

        RequirementExtractor.JobRequirements result = extractor(model).extract("We need Java, Spring Boot and React.");

        assertThat(result.jobTitle()).isEqualTo("Java Full Stack Developer");
        assertThat(result.requirements()).extracting(Requirement::name).containsExactly("Spring Boot", "React");
        assertThat(result.requirements().get(0).searchTerms())
                .containsExactly("@SpringBootApplication", "spring-boot-starter", "Spring Boot");
        // Spring AI appends its JSON format instructions after the job description.
        assertThat(model.lastUserText()).startsWith("We need Java, Spring Boot and React.").contains("JSON");
    }

    @Test
    void cleansUpWhatTheModelSends() {
        List<Requirement> many = IntStream.rangeClosed(1, 14)
                .mapToObj(i -> new Requirement("Skill " + i, List.of("term" + i)))
                .collect(java.util.stream.Collectors.toList());
        many.add(1, new Requirement("  skill 1 ", List.of("duplicate by name")));
        many.add(new Requirement(" ", List.of("no name")));
        many.add(new Requirement("Docker", Arrays.asList(null, "x", "Dockerfile", "Dockerfile", "a".repeat(80))));

        var cleaned = RequirementExtractor.clean(new RequirementExtractor.JobRequirements("  ", many));

        assertThat(cleaned.jobTitle()).isEqualTo("Software role");
        assertThat(cleaned.requirements()).hasSize(RequirementExtractor.MAX_REQUIREMENTS)
                .extracting(Requirement::name).doesNotHaveDuplicates().doesNotContain(" ");
        assertThat(cleaned.requirements().get(0).searchTerms()).containsExactly("term1", "Skill 1");
    }

    @Test
    void keepsAtMostSixSearchTermsAndDropsBadOnes() {
        var raw = new RequirementExtractor.JobRequirements("Dev", List.of(
                new Requirement("Docker", Arrays.asList(null, "x", "Dockerfile", "Dockerfile", "a".repeat(80),
                        "FROM ", "docker-compose", "EXPOSE", "ENTRYPOINT", "CMD"))));

        Requirement docker = RequirementExtractor.clean(raw).requirements().get(0);

        assertThat(docker.searchTerms()).containsExactly("Dockerfile", "FROM", "docker-compose", "EXPOSE", "ENTRYPOINT", "CMD");
    }

    @Test
    void explainsWhenNothingTechnicalWasFound() {
        FakeChatModel model = new FakeChatModel().reply("{\"jobTitle\":\"Manager\",\"requirements\":[]}");

        assertThatThrownBy(() -> extractor(model).extract("Great communicator wanted."))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("No technical requirements");
    }

    @Test
    void reportsRateLimitsAsTemporary() {
        FakeChatModel model = new FakeChatModel().fail(new TransientAiException("HTTP 429 - rate_limit_exceeded"));

        assertThatThrownBy(() -> extractor(model).extract("Java developer"))
                .isInstanceOf(AgentException.class)
                .satisfies(e -> assertThat(((AgentException) e).isTemporary()).isTrue());
    }
}
