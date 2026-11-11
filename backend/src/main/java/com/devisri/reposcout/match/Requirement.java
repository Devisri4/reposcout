package com.devisri.reposcout.match;

import java.util.List;

/**
 * One skill or responsibility from a job description, with words that would appear in code proving it.
 *
 * @param name        short name, e.g. "Spring Boot"
 * @param searchTerms literal text to look for in repos, e.g. "@SpringBootApplication", "spring-boot-starter"
 */
public record Requirement(String name, List<String> searchTerms) {
}
