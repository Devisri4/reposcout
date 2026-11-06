package com.devisri.reposcout.repo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipExtractorTest {

    @TempDir
    Path tmp;

    private Path writeZip(Map<String, String> entries) throws IOException {
        Path zip = tmp.resolve("repo.zip");
        Files.write(zip, TestZips.zip(entries));
        return zip;
    }

    @Test
    void unpacksFilesAndRemovesGitHubsTopFolder() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("demo-main/", null);
        entries.put("demo-main/README.md", "# Demo");
        entries.put("demo-main/src/", null);
        entries.put("demo-main/src/App.java", "class App {}");
        Path out = tmp.resolve("out");

        ZipExtractor.Result result = new ZipExtractor(1_000_000, 1_000, 100).extract(writeZip(entries), out);

        assertThat(result.filesWritten()).isEqualTo(2);
        assertThat(out.resolve("README.md")).hasContent("# Demo");
        assertThat(out.resolve("src/App.java")).hasContent("class App {}");
        assertThat(out.resolve("demo-main")).doesNotExist();
    }

    @Test
    void stopsOnZipSlipPaths() throws IOException {
        Path zip = writeZip(Map.of("demo-main/../../evil.txt", "boom"));
        Path out = tmp.resolve("out");

        assertThatThrownBy(() -> new ZipExtractor(1_000_000, 1_000, 100).extract(zip, out))
                .isInstanceOf(RepoLoadException.class)
                .hasMessageContaining("unsafe path");
        assertThat(tmp.resolve("evil.txt")).doesNotExist();
    }

    @Test
    void skipsSingleFilesOverTheLimit() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("demo-main/small.txt", "ok");
        entries.put("demo-main/big.bin", "x".repeat(500));
        Path out = tmp.resolve("out");

        ZipExtractor.Result result = new ZipExtractor(1_000_000, 100, 100).extract(writeZip(entries), out);

        assertThat(result.filesWritten()).isEqualTo(1);
        assertThat(result.filesSkipped()).isEqualTo(1);
        assertThat(out.resolve("small.txt")).exists();
        assertThat(out.resolve("big.bin")).doesNotExist();
    }

    @Test
    void stopsWhenTotalUnpackedSizeIsTooLarge() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            entries.put("demo-main/file" + i + ".txt", "y".repeat(100));
        }
        Path zip = writeZip(entries);

        assertThatThrownBy(() -> new ZipExtractor(300, 1_000, 100).extract(zip, tmp.resolve("out")))
                .isInstanceOf(RepoLoadException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void stopsWhenThereAreTooManyFiles() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i < 4; i++) {
            entries.put("demo-main/f" + i, "z");
        }
        Path zip = writeZip(entries);

        assertThatThrownBy(() -> new ZipExtractor(1_000_000, 1_000, 3).extract(zip, tmp.resolve("out")))
                .isInstanceOf(RepoLoadException.class)
                .hasMessageContaining("more than 3 files");
    }
}
