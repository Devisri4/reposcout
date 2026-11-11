-- One saved job match. The full result is stored as JSON so the share page shows exactly what was computed.
CREATE TABLE job_match (
    id           VARCHAR(36)              PRIMARY KEY,
    github_user  VARCHAR(100)             NOT NULL,
    job_title    VARCHAR(200)             NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    result_json  TEXT                     NOT NULL
);

CREATE INDEX idx_job_match_created_at ON job_match (created_at);
