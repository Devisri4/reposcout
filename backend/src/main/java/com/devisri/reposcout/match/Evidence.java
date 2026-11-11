package com.devisri.reposcout.match;

/**
 * A real code snippet found in one of the user's repos for one requirement.
 *
 * @param id          short id shown to the AI, e.g. "E3"; bullets may only cite these
 * @param requirement name of the requirement it supports
 * @param repo        owner/name
 * @param path        file path in the repo
 * @param startLine   first line of the snippet
 * @param endLine     last line of the snippet
 * @param matchedTerm the search term that was found
 * @param excerpt     the snippet's lines, shortened
 * @param url         GitHub link to those lines
 */
public record Evidence(String id, String requirement, String repo, String path, int startLine, int endLine,
                       String matchedTerm, String excerpt, String url) {
}
