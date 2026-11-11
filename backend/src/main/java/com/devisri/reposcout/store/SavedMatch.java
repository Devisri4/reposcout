package com.devisri.reposcout.store;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A row in job_match (schema in db/migration/V1__create_job_match.sql). */
@Entity
@Table(name = "job_match")
public class SavedMatch {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "github_user", nullable = false, length = 100)
    private String githubUser;

    @Column(name = "job_title", nullable = false, length = 200)
    private String jobTitle;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    protected SavedMatch() {
        // for JPA
    }

    public SavedMatch(String id, String githubUser, String jobTitle, Instant createdAt, String resultJson) {
        this.id = id;
        this.githubUser = githubUser;
        this.jobTitle = jobTitle;
        this.createdAt = createdAt;
        this.resultJson = resultJson;
    }

    public String getId() {
        return id;
    }

    public String getGithubUser() {
        return githubUser;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getResultJson() {
        return resultJson;
    }
}
