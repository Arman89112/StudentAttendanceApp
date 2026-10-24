package com.attendance.app.database;

import com.attendance.app.model.Semester;
import com.attendance.app.model.Student;
import com.attendance.app.model.Subject;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All SQLite access lives here. The database file is stored inside the
 * project folder so it can be backed up together with the code.
 */
public class DatabaseManager implements AutoCloseable {

    public static final int STATUS_NONE = 0;
    public static final int STATUS_PRESENT = 1;
    public static final int STATUS_ABSENT = 2;
    public static final int STATUS_LATE = 3;

    private final Connection connection;

    public DatabaseManager(File dbFile) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
            initialize();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open database: " + dbFile, e);
        }
    }

    private void initialize() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS semesters (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE
                    )""");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS subjects (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        semester_id INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        week_count INTEGER NOT NULL DEFAULT 8,
                        lesson_count INTEGER NOT NULL DEFAULT 2,
                        FOREIGN KEY(semester_id) REFERENCES semesters(id) ON DELETE CASCADE,
                        UNIQUE(semester_id, name)
                    )""");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS students (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        external_id TEXT NOT NULL UNIQUE,
                        name TEXT NOT NULL
                    )""");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS subject_students (
                        subject_id INTEGER NOT NULL,
                        student_id INTEGER NOT NULL,
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(subject_id, student_id),
                        FOREIGN KEY(subject_id) REFERENCES subjects(id) ON DELETE CASCADE,
                        FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE
                    )""");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS attendance (
                        student_id INTEGER NOT NULL,
                        subject_id INTEGER NOT NULL,
                        week_number INTEGER NOT NULL,
                        lesson_number INTEGER NOT NULL,
                        status INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(student_id, subject_id, week_number, lesson_number),
                        FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE,
                        FOREIGN KEY(subject_id) REFERENCES subjects(id) ON DELETE CASCADE
                    )""");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_att_subject ON attendance(subject_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_roster_subject ON subject_students(subject_id)");
        }
    }

    // ------------------------------------------------------------------
    // Semesters
    // ------------------------------------------------------------------

    public List<Semester> semesters() {
        List<Semester> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT id, name FROM semesters ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Semester(rs.getInt(1), rs.getString(2)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    public int createSemester(String name) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO semesters(name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name.trim());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void renameSemester(int id, String name) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE semesters SET name=? WHERE id=?")) {
            ps.setString(1, name.trim());
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void deleteSemester(int id) {
        exec("DELETE FROM semesters WHERE id=" + id);
    }

    // ------------------------------------------------------------------
    // Subjects
    // ------------------------------------------------------------------

    public List<Subject> subjects(int semesterId) {
        List<Subject> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, semester_id, name, week_count, lesson_count FROM subjects WHERE semester_id=? ORDER BY id")) {
            ps.setInt(1, semesterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Subject(rs.getInt(1), rs.getInt(2), rs.getString(3),
                            rs.getInt(4), rs.getInt(5)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    public int createSubject(int semesterId, String name, int weekCount, int lessonCount) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO subjects(semester_id, name, week_count, lesson_count) VALUES (?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, semesterId);
            ps.setString(2, name.trim());
            ps.setInt(3, weekCount);
            ps.setInt(4, lessonCount);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : -1;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void updateSubject(int id, String name, int weekCount, int lessonCount) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE subjects SET name=?, week_count=?, lesson_count=? WHERE id=?")) {
            ps.setString(1, name.trim());
            ps.setInt(2, weekCount);
            ps.setInt(3, lessonCount);
            ps.setInt(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void deleteSubject(int id) {
        exec("DELETE FROM subjects WHERE id=" + id);
    }

    // ------------------------------------------------------------------
    // Roster
    // ------------------------------------------------------------------

    public List<Student> roster(int subjectId) {
        List<Student> result = new ArrayList<>();
        String sql = """
                SELECT s.id, s.external_id, s.name, ss.sort_order
                  FROM subject_students ss
                  JOIN students s ON s.id = ss.student_id
                 WHERE ss.subject_id = ?
                 ORDER BY ss.sort_order, s.name""";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Student(rs.getInt(1), rs.getString(2), rs.getString(3)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    /**
     * Adds a student to a subject. A student with the same external id is
     * reused instead of duplicated, which keeps ids globally unique.
     * Returns false when the id is already used by another person.
     */
    public boolean addStudent(int subjectId, String externalId, String name) {
        try {
            connection.setAutoCommit(false);
            int studentId;
            Integer existing = findStudentByExternalId(externalId);
            if (existing != null) {
                studentId = existing;
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE students SET name=? WHERE id=?")) {
                    ps.setString(1, name.trim());
                    ps.setInt(2, studentId);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO students(external_id, name) VALUES (?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, externalId);
                    ps.setString(2, name.trim());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        studentId = keys.next() ? keys.getInt(1) : -1;
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO subject_students(subject_id, student_id, sort_order) VALUES (?,?,?)")) {
                ps.setInt(1, subjectId);
                ps.setInt(2, studentId);
                ps.setInt(3, nextSortOrder(subjectId));
                ps.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (SQLException e) {
            rollbackQuietly();
            throw new IllegalStateException(e);
        } finally {
            restoreAutoCommit();
        }
    }

    private int nextSortOrder(int subjectId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(MAX(sort_order),0)+1 FROM subject_students WHERE subject_id=?")) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        }
    }

    private Integer findStudentByExternalId(String externalId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM students WHERE external_id=?")) {
            ps.setString(1, externalId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    public void renameStudent(int studentId, String name) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE students SET name=? WHERE id=?")) {
            ps.setString(1, name);
            ps.setInt(2, studentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Re-numbering is allowed as long as nobody else owns that number.
     */
    public boolean updateExternalId(int studentId, String newExternalId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE students SET external_id=? WHERE id=? AND NOT EXISTS (SELECT 1 FROM students WHERE external_id=? AND id<>?)")) {
            ps.setString(1, newExternalId);
            ps.setInt(2, studentId);
            ps.setString(3, newExternalId);
            ps.setInt(4, studentId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void removeStudentFromSubject(int subjectId, int studentId) {
        exec("DELETE FROM subject_students WHERE subject_id=" + subjectId + " AND student_id=" + studentId);
    }

    /** Copies the roster of one subject into another, keeping existing records. */
    public int copyRoster(int fromSubjectId, int toSubjectId) {
        List<Student> source = roster(fromSubjectId);
        int added = 0;
        for (Student s : source) {
            if (!isInSubject(toSubjectId, s.getDatabaseId()) && addStudent(toSubjectId, s.getExternalId(), s.getName())) {
                added++;
            }
        }
        return added;
    }

    private boolean isInSubject(int subjectId, int studentId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM subject_students WHERE subject_id=? AND student_id=?")) {
            ps.setInt(1, subjectId);
            ps.setInt(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------
    // Attendance
    // ------------------------------------------------------------------

    /**
     * Returns [row][week][lesson] using zero based indexes. The row index
     * follows the order of {@code studentIds}, which keeps gaps in sort_order
     * from silently dropping a student's history.
     */
    public int[][][] attendanceGrid(int subjectId, List<Student> roster, int weekCount, int lessonCount) {
        int[][][] grid = new int[roster.size()][weekCount][lessonCount];
        Map<Integer, Integer> rowOfStudent = new HashMap<>();
        for (int i = 0; i < roster.size(); i++) {
            rowOfStudent.put(roster.get(i).getDatabaseId(), i);
        }
        String sql = """
                SELECT a.student_id, a.week_number, a.lesson_number, a.status
                  FROM attendance a
                 WHERE a.subject_id = ?""";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer row = rowOfStudent.get(rs.getInt(1));
                    int week = rs.getInt(2) - 1;
                    int lesson = rs.getInt(3) - 1;
                    if (row != null && week >= 0 && week < weekCount && lesson >= 0 && lesson < lessonCount) {
                        grid[row][week][lesson] = rs.getInt(4);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return grid;
    }

    public void setStatus(int subjectId, int studentId, int week, int lesson, int status) {
        String sql = """
                INSERT INTO attendance(student_id, subject_id, week_number, lesson_number, status)
                VALUES (?,?,?,?,?)
                ON CONFLICT(student_id, subject_id, week_number, lesson_number)
                DO UPDATE SET status = excluded.status""";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, subjectId);
            ps.setInt(3, week);
            ps.setInt(4, lesson);
            ps.setInt(5, status);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public int getStatus(int subjectId, int studentId, int week, int lesson) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT status FROM attendance WHERE student_id=? AND subject_id=? AND week_number=? AND lesson_number=?")) {
            ps.setInt(1, studentId);
            ps.setInt(2, subjectId);
            ps.setInt(3, week);
            ps.setInt(4, lesson);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : STATUS_NONE;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------
    // Cross subject summary
    // ------------------------------------------------------------------

    public record StudentProfile(String externalId, String name, int subjects,
                                 int marked, int present, int late) {
        public double rate() {
            return marked == 0 ? -1 : present * 100.0 / marked;
        }
    }

    /** One row per student, totalling every subject they appear in. */
    public List<StudentProfile> profiles() {
        List<StudentProfile> result = new ArrayList<>();
        String sql = """
                SELECT s.external_id, s.name,
                       (SELECT COUNT(*) FROM subject_students ss WHERE ss.student_id = s.id),
                       COALESCE(SUM(CASE WHEN a.status IN (1,2,3) THEN 1 ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN a.status IN (1,3) THEN 1 ELSE 0 END), 0),
                       COALESCE(SUM(CASE WHEN a.status = 3 THEN 1 ELSE 0 END), 0)
                  FROM students s
                  LEFT JOIN attendance a ON a.student_id = s.id
                 GROUP BY s.id
                 ORDER BY s.name, s.external_id""";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new StudentProfile(rs.getString(1), rs.getString(2),
                        rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6)));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void exec(String sql) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private void restoreAutoCommit() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
