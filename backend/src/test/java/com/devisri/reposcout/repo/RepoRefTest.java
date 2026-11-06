package com.devisri.reposcout.repo;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepoRefTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://github.com/spring-projects/spring-petclinic",
            "https://github.com/spring-projects/spring-petclinic/",
            "https://github.com/spring-projects/spring-petclinic.git",
            "http://www.github.com/spring-projects/spring-petclinic/tree/main/src",
            "github.com/spring-projects/spring-petclinic?tab=readme",
            "  spring-projects/spring-petclinic  ",
    })
    void parsesCommonLinkShapes(String input) {
        RepoRef ref = RepoRef.parse(input);

        assertThat(ref.owner()).isEqualTo("spring-projects");
        assertThat(ref.name()).isEqualTo("spring-petclinic");
    }

    @ParameterizedTest
    @ValueSource(strings = {"socket.io", "my_repo.js", "a.b-c"})
    void keepsDotsInRepoNames(String name) {
        assertThat(RepoRef.parse("https://github.com/owner/" + name).name()).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "spring-petclinic",
            "https://gitlab.com/owner/repo",
            "https://github.com/owner",
            "https://github.com/../etc",
            "https://github.com/owner/..",
            "owner/repo name",
    })
    void rejectsAnythingElse(String input) {
        assertThatThrownBy(() -> RepoRef.parse(input)).isInstanceOf(RepoLoadException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Spring-Projects/Spring-PetClinic", "spring-projects/spring-petclinic"})
    void cacheKeyIgnoresCase(String input) {
        assertThat(RepoRef.parse(input).cacheKey()).isEqualTo("spring-projects/spring-petclinic");
    }
}
