package com.attendance.app.database;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Excel import / export. Import tries hard to recognise the layout: the name
 * column and the student id column can be anywhere, and the header may or may
 * not exist. Attendance columns are named W1-L1, W1L1, 1-1, ... as produced by
 * {@link #export}.
 */
public final class ExcelIO {

    private static final Pattern HEADER_ID = Pattern.compile(
            "(学号|编号|id|student\\s*id|roll|number|no\\.?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_NAME = Pattern.compile(
            "(姓名|名字|学生|name|student)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEEK_LESSON = Pattern.compile(
            "(?i)w(\\d+)\\s*[-_ ]?\\s*l(\\d+)");

    public record RosterRow(String name, String externalId, Map<Long, Integer> statuses) {
    }

    public record ImportResult(int students, int cells, boolean headerFound) {
    }

    private ExcelIO() {
    }

    // ------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------

    public static List<RosterRow> read(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return List.of();
            }
            Detection detection = detect(sheet);
            List<RosterRow> rows = new ArrayList<>();
            for (int r = detection.firstDataRow(); r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String name = text(row.getCell(detection.nameColumn()));
                String id = text(row.getCell(detection.idColumn()));
                if (id == null || id.isBlank()) {
                    id = name;
                }
                if (name == null || name.isBlank()) {
                    continue;
                }
                Map<Long, Integer> statuses = readStatuses(sheet, detection.headerRow(), row, detection.offset());
                rows.add(new RosterRow(name.trim(), id.trim(), statuses));
            }
            return rows;
        }
    }

    /**
     * Matches the column headers of the header row against W1-L1 style names,
     * then reads the value of the same column out of the data row.
     */
    private static Map<Long, Integer> readStatuses(Sheet sheet, int headerRow, Row dataRow, int startColumn) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (headerRow < 0) {
            return result;
        }
        Row header = sheet.getRow(headerRow);
        if (header == null) {
            return result;
        }
        for (int c = startColumn; c < header.getLastCellNum(); c++) {
            Matcher matcher = WEEK_LESSON.matcher(header(header.getCell(c)));
            if (!matcher.find()) {
                continue;
            }
            int week = Integer.parseInt(matcher.group(1));
            int lesson = Integer.parseInt(matcher.group(2));
            int status = parseStatus(text(dataRow.getCell(c)));
            if (status >= 0) {
                result.put(cellKey(week, lesson), status);
            }
        }
        return result;
    }

    private static long cellKey(int week, int lesson) {
        return ((long) week << 32) | (lesson & 0xFFFFFFFFL);
    }

    private static int parseStatus(String value) {
        if (value == null) return -1;
        String v = value.trim().toLowerCase();
        if (v.isEmpty()) return -1;
        return switch (v) {
            case "p", "y", "1", "到", "出勤", "√", "v", "\u2705" -> DatabaseManager.STATUS_PRESENT;
            case "a", "n", "0", "缺", "缺席", "x", "\u274c" -> DatabaseManager.STATUS_ABSENT;
            case "l", "3", "迟", "迟到", "\ud83c\udfc3", "\ud83c\udfc3\u200d\u2642\ufe0f" -> DatabaseManager.STATUS_LATE;
            default -> -1;
        };
    }

    /**
     * Works out which column holds names, which holds ids, from which row the
     * data starts, and where the attendance columns begin.
     */
    private static Detection detect(Sheet sheet) {
        int nameColumn = -1;
        int idColumn = -1;
        int headerRow = -1;
        for (int r = 0; r <= Math.min(9, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            int rowName = -1;
            int rowId = -1;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String value = header(row.getCell(c));
                if (value.isBlank()) continue;
                if (rowId < 0 && HEADER_ID.matcher(value).find()) {
                    rowId = c;
                } else if (rowName < 0 && HEADER_NAME.matcher(value).find()) {
                    rowName = c;
                }
            }
            if (rowName >= 0 && rowId >= 0) {
                nameColumn = rowName;
                idColumn = rowId;
                headerRow = r;
                break;
            }
        }

        if (nameColumn < 0 || idColumn < 0) {
            Guess guess = guessColumns(sheet);
            nameColumn = guess.name;
            idColumn = guess.id;
            headerRow = guess.headerRow;
        }

        int firstDataRow = headerRow >= 0 ? headerRow + 1 : 0;
        int offset = Math.max(nameColumn, idColumn) + 1;
        return new Detection(nameColumn, idColumn, firstDataRow, headerRow, offset);
    }

    /** No header: the column that is mostly digits is the student id. */
    private static Guess guessColumns(Sheet sheet) {
        int lastCol = 0;
        for (int r = 0; r <= Math.min(9, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row != null) lastCol = Math.max(lastCol, row.getLastCellNum());
        }
        int id = -1;
        int name = -1;
        for (int c = 0; c < lastCol; c++) {
            int digits = 0;
            int filled = 0;
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String value = text(row.getCell(c));
                if (value == null || value.isBlank()) continue;
                filled++;
                if (value.matches("\\d{2,}")) digits++;
            }
            if (filled == 0) continue;
            if (digits * 2 > filled && id < 0) {
                id = c;
            } else if (name < 0 && c != id) {
                name = c;
            }
        }
        if (name < 0) name = 0;
        if (id < 0) id = name == 0 ? 1 : 0;
        int header = -1;
        String first = text(rowCell(sheet, 0, name));
        if (first != null && (HEADER_NAME.matcher(first).find() || HEADER_ID.matcher(first).find())) {
            header = 0;
        }
        return new Guess(name, id, header);
    }

    private static Cell rowCell(Sheet sheet, int r, int c) {
        Row row = sheet.getRow(r);
        if (row == null) return null;
        return row.getCell(c);
    }

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    public static void export(File file, String subjectName, List<String> headers,
                              List<String[]> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(safeSheetName(subjectName));
            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                head.createCell(i).setCellValue(headers.get(i));
            }
            for (int i = 0; i < rows.size(); i++) {
                Row row = sheet.createRow(i + 1);
                String[] values = rows.get(i);
                for (int c = 0; c < values.length; c++) {
                    row.createCell(c).setCellValue(values[c] == null ? "" : values[c]);
                }
            }
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                workbook.write(out);
            }
        }
    }

    private static String safeSheetName(String name) {
        String cleaned = name.replaceAll("[\\\\/*?:\\[\\]]", " ").trim();
        return cleaned.isEmpty() ? "Attendance" : cleaned.substring(0, Math.min(31, cleaned.length()));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String header(Cell cell) {
        return text(cell) == null ? "" : text(cell).trim();
    }

    private static String text(Cell cell) {
        if (cell == null) return null;
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                if (value == Math.floor(value)) {
                    yield String.valueOf((long) value);
                }
                yield String.valueOf(value);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private record Detection(int nameColumn, int idColumn, int firstDataRow, int headerRow, int offset) {
    }

    private record Guess(int name, int id, int headerRow) {
    }
}
