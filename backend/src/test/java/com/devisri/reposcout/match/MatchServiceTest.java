package com.devisri.reposcout.match;

import java.util.List;

import com.devisri.reposcout.agent.AgentException;
import com.devisri.reposcout.agent.AgentProperties;
import com.devisri.reposcout.repo.RepoLoadException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MatchServiceTest {

    private static final String REQUIREMENTS_JSON = """
            {"jobTitle":"Java Developer","requirements":[
              {"name":"Spring Boot","searchTerms":["spring-boot-starter"]},
              {"name":"Kubernetes","searchTerms":["kubectl"]}]}
            """;

    // Evidence excerpts contain Java code with braces; they must reach the model untouched.
    private static final Evidence CODE = new Evidence("E1", "Spring Boot", "devi/shop", "src/Shop.java", 1, 3,
            "spring-boot-starter", "1: @SpringBootApplication\n2: public class Shop { void run() { } }\n3: // {placeholder}",
            "https://github.com/devi/shop/blob/main/src/Shop.java#L1-L3");

    private final EvidenceFinder finder = mock(EvidenceFinder.class);
    private final com.devisri.reposcout.store.MatchStore store = mock(com.devisri.reposcout.store.MatchStore.class);

    private MatchService service(FakeChatModel model, String key) {
        ChatClient.Builder builder = ChatClient.builder(model);
        return new MatchService(new RequirementExtractor(builder), finder, new BulletWriter(builder), new AgentProperties(15, key), store);
    }

    @Test
    void runsTheWholeMatchAndKeepsOnlyProvenBullets() {
        FakeChatModel model = new FakeChatModel().reply(REQUIREMENTS_JSON).reply("""
                {"bullets":[
                  {"text":"Built a shop backend with Spring Boot.","requirement":"Spring Boot","evidenceIds":["E1"]},
                  {"text":"Ran it on Kubernetes.","requirement":"Kubernetes","evidenceIds":["E7"]}]}
                """);
        when(finder.find(eq("Devisri4"), anyList()))
                .thenReturn(new EvidenceFinder.Result(List.of("devi/shop"), List.of(), List.of(CODE),
                        java.util.Map.of("devi/shop", "Online shop with Spring Boot and React")));

        MatchResult result = service(model, "gsk_test").match("https://github.com/Devisri4", "We need a Java developer with Spring Boot and Kubernetes.");

        assertThat(result.id()).isNotBlank();
        assertThat(result.githubUser()).isEqualTo("Devisri4");
        assertThat(result.jobTitle()).isEqualTo("Java Developer");
        assertThat(result.bullets()).extracting(MatchResult.Bullet::text).containsExactly("Built a shop backend with Spring Boot.");
        assertThat(result.dropped()).extracting(MatchResult.DroppedClaim::text).containsExactly("Ran it on Kubernetes.");
        assertThat(result.gaps()).containsExactly("Kubernetes");
        assertThat(result.saved()).isFalse(); // the mocked store did not save it
        org.mockito.Mockito.verify(store).save(org.mockito.ArgumentMatchers.argThat(r -> r.id().equals(result.id())));
        // The second AI call saw the real code, braces and all.
        assertThat(model.prompts).hasSize(2);
        assertThat(model.lastUserText()).contains("PROJECTS:\n- devi/shop: Online shop with Spring Boot and React")
                .contains("E1 (for Spring Boot) devi/shop / src/Shop.java lines 1-3:")
                .contains("public class Shop { void run() { } }").contains("// {placeholder}");
    }

    @Test
    void skipsTheSecondAiCallWhenNoEvidenceWasFound() {
        FakeChatModel model = new FakeChatModel().reply(REQUIREMENTS_JSON);
        when(finder.find(eq("devi"), anyList())).thenReturn(new EvidenceFinder.Result(List.of("devi/shop"), List.of(), List.of()));

        MatchResult result = service(model, "gsk_test").match("devi", "We need a Java developer with Spring Boot and Kubernetes.");

        assertThat(model.prompts).hasSize(1);
        assertThat(result.bullets()).isEmpty();
        assertThat(result.gaps()).containsExactly("Spring Boot", "Kubernetes");
    }

    @Test
    void refusesWithoutAnApiKeyBeforeTouchingGitHub() {
        assertThatThrownBy(() -> service(new FakeChatModel(), AgentException.KEY_PLACEHOLDER).match("devi", "x".repeat(50)))
                .isInstanceOf(AgentException.class).hasMessageContaining("GROQ_API_KEY");
        verifyNoInteractions(finder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Devisri4", "@Devisri4", "https://github.com/Devisri4", "github.com/Devisri4/", " devisri4 "})
    void acceptsCommonWaysOfWritingAUsername(String input) {
        assertThat(MatchService.cleanUser(input)).isEqualToIgnoringCase("Devisri4");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-devi", "devi-", "de vi", "../etc", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "https://gitlab.com/devi"})
    void rejectsAnythingThatIsNotAGitHubUsername(String input) {
        assertThatThrownBy(() -> MatchService.cleanUser(input)).isInstanceOf(RepoLoadException.class);
    }
}
