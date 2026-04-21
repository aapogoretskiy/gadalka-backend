CREATE TABLE user_profile_goals
(
    user_profile_id BIGINT      NOT NULL,
    goal            VARCHAR(50) NOT NULL,
    CONSTRAINT fk_profile_goal FOREIGN KEY (user_profile_id) REFERENCES user_profile (id) ON DELETE CASCADE
);