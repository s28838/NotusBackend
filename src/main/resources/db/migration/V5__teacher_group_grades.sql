CREATE TABLE IF NOT EXISTS grades (
    id BIGSERIAL PRIMARY KEY,
    clerk_user_id VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    grade_value VARCHAR(20) NOT NULL,
    issue_date TIMESTAMP NOT NULL DEFAULT NOW(),
    is_new BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE grades ADD COLUMN IF NOT EXISTS student_id BIGINT REFERENCES students(id);
ALTER TABLE grades ADD COLUMN IF NOT EXISTS teacher_id BIGINT REFERENCES teachers(id);
ALTER TABLE grades ADD COLUMN IF NOT EXISTS group_id BIGINT REFERENCES teacher_groups(id);
ALTER TABLE grades ADD COLUMN IF NOT EXISTS numeric_value DECIMAL(4,2);
ALTER TABLE grades ADD COLUMN IF NOT EXISTS weight INT NOT NULL DEFAULT 1;
ALTER TABLE grades ADD COLUMN IF NOT EXISTS semester VARCHAR(50) NOT NULL DEFAULT '1';
ALTER TABLE grades ADD COLUMN IF NOT EXISTS source_type VARCHAR(50) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE grades ADD COLUMN IF NOT EXISTS source_id BIGINT;
ALTER TABLE grades ADD COLUMN IF NOT EXISTS title VARCHAR(255);
ALTER TABLE grades ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE grades ADD COLUMN IF NOT EXISTS comment TEXT;
ALTER TABLE grades ADD COLUMN IF NOT EXISTS grade_date DATE;
ALTER TABLE grades ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE grades ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
ALTER TABLE grades ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

UPDATE grades SET grade_date = DATE(issue_date) WHERE grade_date IS NULL;

CREATE INDEX IF NOT EXISTS idx_grades_student_id ON grades(student_id);
CREATE INDEX IF NOT EXISTS idx_grades_teacher_id ON grades(teacher_id);
CREATE INDEX IF NOT EXISTS idx_grades_group_id ON grades(group_id);
CREATE INDEX IF NOT EXISTS idx_grades_source ON grades(source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_grades_group_student ON grades(group_id, student_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_quiz_grade_per_student
ON grades(student_id, group_id, source_type, source_id)
WHERE source_type = 'QUIZ' AND deleted_at IS NULL;

ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS group_id BIGINT REFERENCES teacher_groups(id);
ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS count_as_grade BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS grade_weight INT;
ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS grade_semester VARCHAR(50);
