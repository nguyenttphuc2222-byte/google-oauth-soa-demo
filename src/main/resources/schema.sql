CREATE TABLE IF NOT EXISTS lecturer_directory (
 email VARCHAR(320) PRIMARY KEY,
 last_login_at VARCHAR(50) NOT NULL
);
CREATE TABLE IF NOT EXISTS submissions (
 id VARCHAR(36) PRIMARY KEY, submitted_at VARCHAR(50) NOT NULL, student_sub VARCHAR(255) NOT NULL,
 student_name VARCHAR(255) NOT NULL, student_email VARCHAR(320) NOT NULL, group_name VARCHAR(100) NOT NULL,
 topic VARCHAR(300) NOT NULL, lecturer_email VARCHAR(320) NOT NULL,
 file_id VARCHAR(200) NOT NULL DEFAULT '', file_name VARCHAR(180) NOT NULL DEFAULT '',
 file_url VARCHAR(600) NOT NULL DEFAULT '', file_size BIGINT NOT NULL DEFAULT 0,
 phase VARCHAR(20) NOT NULL DEFAULT 'DRAFT', cc_self BOOLEAN NOT NULL DEFAULT FALSE,
 file_download_url VARCHAR(2000) NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS drive_admin_grant (
 id INTEGER PRIMARY KEY CHECK (id = 1), owner_email VARCHAR(320) NOT NULL,
 owner_sub VARCHAR(255) NOT NULL, root_id VARCHAR(200) NOT NULL,
 mode VARCHAR(20) NOT NULL, client_id VARCHAR(320) NOT NULL,
 refresh_cipher VARCHAR(12000) NOT NULL
);
CREATE TABLE IF NOT EXISTS drive_student_folders (
 root_id VARCHAR(200) NOT NULL, student_key VARCHAR(64) NOT NULL,
 folder_id VARCHAR(200) NOT NULL, folder_name VARCHAR(500) NOT NULL,
 PRIMARY KEY(root_id, student_key)
);
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS cc_self BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS file_download_url VARCHAR(2000) NOT NULL DEFAULT '';
CREATE INDEX IF NOT EXISTS submissions_owner ON submissions(student_sub);
CREATE INDEX IF NOT EXISTS submissions_assignee ON submissions(lecturer_email, phase);
CREATE TABLE IF NOT EXISTS reviews (
 id VARCHAR(36) PRIMARY KEY, submission_id VARCHAR(36) NOT NULL UNIQUE REFERENCES submissions(id),
 reviewed_at VARCHAR(50) NOT NULL, reviewer_sub VARCHAR(255) NOT NULL, reviewer_email VARCHAR(320) NOT NULL,
 decision VARCHAR(20) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
 comment_text VARCHAR(2000) NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS mail_notices (
 id VARCHAR(36) PRIMARY KEY, submission_id VARCHAR(36) NOT NULL REFERENCES submissions(id),
 kind VARCHAR(20) NOT NULL CHECK (kind IN ('SUBMISSION', 'REVIEW')),
 sender_sub VARCHAR(255) NOT NULL, sender_email VARCHAR(320) NOT NULL, to_email VARCHAR(320) NOT NULL,
 cc_email VARCHAR(320) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 message_id VARCHAR(255) NOT NULL DEFAULT '', error_code VARCHAR(100) NOT NULL DEFAULT '',
 UNIQUE (submission_id, kind)
);
