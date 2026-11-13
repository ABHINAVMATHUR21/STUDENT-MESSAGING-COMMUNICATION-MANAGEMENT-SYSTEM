
-- =====================================
-- STUDENT MESSAGING SYSTEM DATABASE
-- =====================================

CREATE DATABASE IF NOT EXISTS student_messaging;

USE student_messaging;

-- =====================================
-- 1. USERS TABLE
-- =====================================

CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    section_id INT NULL,
    staff_role VARCHAR(100) NULL
);

-- =====================================
-- 2. SECTIONS TABLE
-- =====================================

CREATE TABLE sections (
    section_id INT AUTO_INCREMENT PRIMARY KEY,
    section_name VARCHAR(50) NOT NULL UNIQUE
);

-- =====================================
-- 3. TEACHER_SECTIONS TABLE
-- =====================================

CREATE TABLE teacher_sections (
    user_id INT NOT NULL,
    section_id INT NOT NULL,

    PRIMARY KEY (user_id, section_id),

    FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    FOREIGN KEY (section_id)
        REFERENCES sections(section_id)
);

-- =====================================
-- 4. MESSAGES TABLE
-- =====================================

CREATE TABLE messages (
    message_id INT AUTO_INCREMENT PRIMARY KEY,

    sender_id INT NOT NULL,
    receiver_id INT NULL,

    receiver_type VARCHAR(20) NOT NULL,

    section_id INT NULL,

    message_body TEXT NOT NULL,

    sent_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    edited TINYINT(1) DEFAULT 0,
    deleted TINYINT(1) DEFAULT 0,

    FOREIGN KEY (sender_id)
        REFERENCES users(user_id),

    FOREIGN KEY (receiver_id)
        REFERENCES users(user_id),

    FOREIGN KEY (section_id)
        REFERENCES sections(section_id)
);



-- =====================================
-- DATABASE INDEXES
-- =====================================

CREATE INDEX idx_messages_sender
ON messages(sender_id);

CREATE INDEX idx_messages_receiver
ON messages(receiver_id);

CREATE INDEX idx_messages_section
ON messages(section_id);

CREATE INDEX idx_messages_sent_at
ON messages(sent_at);
