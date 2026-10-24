import sqlite3
import os
import random

db_path = "attendance.db"
if os.path.exists(db_path):
    os.remove(db_path)

conn = sqlite3.connect(db_path)
cursor = conn.cursor()

# Create tables matching DatabaseManager schema
cursor.executescript("""
CREATE TABLE IF NOT EXISTS semesters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS subjects (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    semester_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    week_count INTEGER NOT NULL DEFAULT 8,
    lesson_count INTEGER NOT NULL DEFAULT 2,
    FOREIGN KEY(semester_id) REFERENCES semesters(id) ON DELETE CASCADE,
    UNIQUE(semester_id, name)
);
CREATE TABLE IF NOT EXISTS students (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS subject_students (
    subject_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY(subject_id, student_id),
    FOREIGN KEY(subject_id) REFERENCES subjects(id) ON DELETE CASCADE,
    FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS attendance (
    student_id INTEGER NOT NULL,
    subject_id INTEGER NOT NULL,
    week_number INTEGER NOT NULL,
    lesson_number INTEGER NOT NULL,
    status INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY(student_id, subject_id, week_number, lesson_number),
    FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY(subject_id) REFERENCES subjects(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_att_subject ON attendance(subject_id);
CREATE INDEX IF NOT EXISTS idx_roster_subject ON subject_students(subject_id);
""")

# Insert Semester
cursor.execute("INSERT INTO semesters (name) VALUES (?)", ("Fall 2026 Semester",))
semester_id = cursor.lastrowid

# Insert Subjects
subjects_data = [
    ("Computer Science 101", 8, 2),
    ("Data Structures & Algorithms", 10, 2),
    ("Software Engineering", 8, 2)
]
subject_ids = []
for name, weeks, lessons in subjects_data:
    cursor.execute("INSERT INTO subjects (semester_id, name, week_count, lesson_count) VALUES (?, ?, ?, ?)",
                   (semester_id, name, weeks, lessons))
    subject_ids.append(cursor.lastrowid)

# Insert Students
students_data = [
    ("S1001", "Alice Johnson"),
    ("S1002", "Bob Smith"),
    ("S1003", "Charlie Davis"),
    ("S1004", "Diana Prince"),
    ("S1005", "Ethan Hunt"),
    ("S1006", "Fiona Gallagher"),
    ("S1007", "George Washington"),
    ("S1008", "Hannah Abbott")
]
student_ids = []
for ext_id, name in students_data:
    cursor.execute("INSERT INTO students (external_id, name) VALUES (?, ?)", (ext_id, name))
    student_ids.append(cursor.lastrowid)

# Assign students to subjects and add sample attendance
for idx, subj_id in enumerate(subject_ids):
    for order, stud_id in enumerate(student_ids):
        cursor.execute("INSERT INTO subject_students (subject_id, student_id, sort_order) VALUES (?, ?, ?)",
                       (subj_id, stud_id, order))
        
        random.seed(stud_id * 100 + subj_id)
        for week in range(1, 3):
            for lesson in range(1, 3):
                status = random.choice([1, 1, 1, 2, 3]) # Present, Absent, Late
                cursor.execute("""
                    INSERT OR REPLACE INTO attendance (student_id, subject_id, week_number, lesson_number, status)
                    VALUES (?, ?, ?, ?, ?)
                """, (stud_id, subj_id, week, lesson, status))

conn.commit()
conn.close()
print("Demo database initialized successfully!")
