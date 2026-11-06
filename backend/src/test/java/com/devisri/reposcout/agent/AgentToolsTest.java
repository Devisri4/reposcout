package com.devisri.reposcout.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.devisri.reposcout.repo.LoadedRepo;
import com.devisri.reposcout.repo.RepoLoader;
import com.devisri.reposcout.tools.RepoTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AgentToolsTest {

    @TempDir
    Path tmp;

    @Test
    void stopsRunningToolsOnceTheBudgetIsUsedUp() throws IOException {
        Files.writeString(tmp.resolve("App.java"), "class App {}\n");
        AgentTools tools = new AgentTools(new RepoTools(new LoadedRepo("a", "b", "main", tmp)), 3);

        assertThat(tools.listFiles("")).contains("App.java");
        assertThat(tools.searchCode("class")).contains("App.java:1");
        assertThat(tools.readFile("App.java", 1, 1)).contains("1: class App {}");
        assertThat(tools.readFile("App.java", 1, 1)).isEqualTo(AgentTools.BUDGET_USED_UP);
        assertThat(tools.listFiles("")).isEqualTo(AgentTools.BUDGET_USED_UP);
        assertThat(tools.callCount()).isEqualTo(5);
    }

    @Test
    void refusesToRunWithoutAnApiKeyBeforeDownloadingAnything() {
        RepoLoader loader = mock(RepoLoader.class);
        RepoAgent agent = new RepoAgent(mock(ChatClient.Builder.class, org.mockito.Answers.RETURNS_MOCKS), loader,
                new AgentProperties(15, AgentException.KEY_PLACEHOLDER));

        assertThatThrownBy(() -> agent.ask("acme/demo", "How does login work?"))
                .isInstanceOf(AgentException.class)
                .hasMessageContaining("GROQ_API_KEY");
        verifyNoInteractions(loader);
    }
}
