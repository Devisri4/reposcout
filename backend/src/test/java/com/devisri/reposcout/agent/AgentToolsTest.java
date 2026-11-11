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
    void logsEveryStepAndRemembersWhichLinesWereRead() throws IOException {
        Files.writeString(tmp.resolve("App.java"), "class App {}\nvoid run() {}\n");
        AgentTools tools = new AgentTools(new RepoTools(new LoadedRepo("a", "b", "main", tmp)), 15);

        tools.listFiles("");
        tools.searchCode("run");
        tools.readFile("App.java", 1, 2);
        tools.readFile("Missing.java", 1, 2);

        assertThat(tools.steps()).extracting(AgentStep::number, AgentStep::tool, AgentStep::summary).containsExactly(
                org.assertj.core.groups.Tuple.tuple(1, "listFiles", "1 entries"),
                org.assertj.core.groups.Tuple.tuple(2, "searchCode", "1 match(es)"),
                org.assertj.core.groups.Tuple.tuple(3, "readFile", "read lines 1-2"),
                org.assertj.core.groups.Tuple.tuple(4, "readFile",
                        "Error: file 'Missing.java' does not exist. Use listFiles or searchCode to find the right path."));
        assertThat(tools.readRanges().covers("App.java", 1, 2)).isTrue();
        assertThat(tools.readRanges().readAny("Missing.java")).isFalse();
    }

    @Test
    void showsTheModelTheParameterNamesItNaturallyUses() {
        AgentTools tools = new AgentTools(new RepoTools(new LoadedRepo("a", "b", "main", tmp)), 15);

        String schema = java.util.Arrays.stream(org.springframework.ai.support.ToolCallbacks.from(tools))
                .filter(t -> t.getToolDefinition().name().equals("readFile"))
                .findFirst().orElseThrow()
                .getToolDefinition().inputSchema();

        // gpt-oss writes line_start / line_end; other names were rejected by Groq as tool_use_failed.
        assertThat(schema).contains("\"line_start\"").contains("\"line_end\"").doesNotContain("startLine");
        assertThat(schema).containsPattern("\"required\"\\s*:\\s*\\[\\s*\"path\"\\s*\\]");
    }

    @Test
    void readsFromTheTopWhenTheModelLeavesOutLineNumbers() throws IOException {
        Files.writeString(tmp.resolve("App.java"), "class App {}\nvoid run() {}\n");
        AgentTools tools = new AgentTools(new RepoTools(new LoadedRepo("a", "b", "main", tmp)), 15);

        assertThat(tools.readFile("App.java", null, null)).startsWith("File: App.java (lines 1-2 of 2)");
        assertThat(tools.readRanges().covers("App.java", 1, 2)).isTrue();
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
