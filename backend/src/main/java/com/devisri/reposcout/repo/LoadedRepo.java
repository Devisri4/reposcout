package com.devisri.reposcout.repo;

import java.nio.file.Path;

/**
 * A repository that has been downloaded and unpacked on the server.
 *
 * @param owner  owner as GitHub spells it
 * @param name   repo name as GitHub spells it
 * @param branch default branch the zip was taken from (used to build file links)
 * @param root   folder holding the unpacked files
 */
public record LoadedRepo(String owner, String name, String branch, Path root) {

    public String fullName() {
        return owner + "/" + name;
    }
}
