package com.devisri.reposcout.repo;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for downloading repositories ({@code reposcout.repo.*} in application.properties).
 *
 * @param workDir          folder where repos are unpacked and cached
 * @param maxRepoSizeKb    largest repo accepted, using the size GitHub reports (in KB)
 * @param maxDownloadBytes hard stop while downloading the zip
 * @param maxUnzippedBytes hard stop for the total size of unpacked files
 * @param maxFileBytes     single files bigger than this are skipped (usually binaries or data)
 * @param maxFiles         most files a repo may contain
 * @param githubToken      optional token; raises the GitHub API limit from 60 to 5000 calls/hour
 * @param githubApiUrl     GitHub API base URL (changed only in tests)
 * @param githubDownloadUrl GitHub zip download base URL (changed only in tests)
 */
@ConfigurationProperties("reposcout.repo")
public record RepoProperties(
        Path workDir,
        @DefaultValue("51200") long maxRepoSizeKb,
        @DefaultValue("104857600") long maxDownloadBytes,
        @DefaultValue("209715200") long maxUnzippedBytes,
        @DefaultValue("1048576") long maxFileBytes,
        @DefaultValue("20000") int maxFiles,
        @DefaultValue("") String githubToken,
        @DefaultValue("https://api.github.com") String githubApiUrl,
        @DefaultValue("https://codeload.github.com") String githubDownloadUrl) {
}
