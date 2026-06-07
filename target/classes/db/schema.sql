-- V1__init_schema.sql
-- Initial database schema for Smart Voice (MySQL)

CREATE TABLE users (
    id            VARCHAR(36) PRIMARY KEY,
    username      VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(100),
    english_level VARCHAR(20) DEFAULT 'INTERMEDIATE',
    avatar_url    VARCHAR(500),
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE scenarios (
    id                VARCHAR(36) PRIMARY KEY,
    category          VARCHAR(50) NOT NULL,
    title             VARCHAR(200) NOT NULL,
    title_cn          VARCHAR(200),
    description       TEXT,
    ai_role           TEXT NOT NULL,
    user_role         TEXT NOT NULL,
    suggested_prompts JSON,
    difficulty        VARCHAR(20) DEFAULT 'INTERMEDIATE',
    icon_url          VARCHAR(500),
    is_active         TINYINT(1) DEFAULT 1,
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE sessions (
    id            VARCHAR(36) PRIMARY KEY,
    user_id       VARCHAR(36) NOT NULL,
    scenario_id   VARCHAR(36) NOT NULL,
    status        VARCHAR(20) DEFAULT 'ACTIVE',
    difficulty    VARCHAR(20) DEFAULT 'INTERMEDIATE',
    started_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    ended_at      DATETIME,
    duration_sec  INT,
    metadata      JSON,
    INDEX idx_sessions_user (user_id, started_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE conversation_turns (
    id                  VARCHAR(36) PRIMARY KEY,
    session_id          VARCHAR(36) NOT NULL,
    turn_index          INT NOT NULL,
    user_text           TEXT,
    ai_text             TEXT,
    user_audio_url      VARCHAR(500),
    ai_audio_url        VARCHAR(500),
    asr_confidence      DECIMAL(4,3),
    asr_duration_ms     INT,
    pronunciation_score DECIMAL(3,1),
    fluency_score       DECIMAL(3,1),
    grammar_issues      JSON,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_turns_session (session_id, turn_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE session_reports (
    id                     VARCHAR(36) PRIMARY KEY,
    session_id             VARCHAR(36) UNIQUE NOT NULL,
    overall_score          DECIMAL(3,1),
    pronunciation_score    DECIMAL(3,1),
    fluency_score          DECIMAL(3,1),
    grammar_score          DECIMAL(3,1),
    vocabulary_score       DECIMAL(3,1),
    comprehension_score    DECIMAL(3,1),
    total_turns            INT,
    total_words            INT,
    unique_words           INT,
    avg_sentence_length    DECIMAL(4,1),
    strengths              JSON,
    weaknesses             JSON,
    grammar_detail         JSON,
    pronunciation_detail   JSON,
    vocabulary_suggestions JSON,
    teacher_comment        TEXT,
    previous_avg_score     DECIMAL(3,1),
    score_change           DECIMAL(3,1),
    created_at             DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE progress_snapshots (
    id                VARCHAR(36) PRIMARY KEY,
    user_id           VARCHAR(36) NOT NULL,
    period            VARCHAR(20) NOT NULL,
    period_start      DATE NOT NULL,
    period_end        DATE NOT NULL,
    session_count     INT,
    avg_overall       DECIMAL(3,1),
    avg_pronunciation DECIMAL(3,1),
    avg_fluency       DECIMAL(3,1),
    avg_grammar       DECIMAL(3,1),
    avg_vocabulary    DECIMAL(3,1),
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_progress_user (user_id, period_start DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
