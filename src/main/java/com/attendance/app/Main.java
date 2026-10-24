package com.attendance.app;

import com.attendance.app.database.DatabaseManager;
import com.attendance.app.database.ExcelIO;
import com.attendance.app.i18n.Lang;
import com.attendance.app.model.Semester;
import com.attendance.app.model.Student;
import com.attendance.app.model.Subject;
import com.attendance.app.ui.AttendanceTable;
import com.attendance.app.ui.ProfileWindow;
import com.attendance.app.ui.Sidebar;
import com.attendance.app.ui.StatsDialog;
import com.attendance.app.print.PdfExporter;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main extends Application {

    private DatabaseManager db;
    private Stage stage;
    private Sidebar sidebar;
    private AttendanceTable table;
    private Label contextBadge;
    private Subject currentSubject;
    private boolean dark = false;
    private Scene scene;
    private double zoom = 1.0;

    @Override
    public void start(Stage primaryStage) {
        db = new DatabaseManager(databaseFile());
        stage = primaryStage;
        stage.setTitle(Lang.get("app.title"));
        render();
        stage.show();
    }

    /** Rebuilds the whole scene, used at start up and after language/theme changes. */
    private void render() {
        sidebar = new Sidebar(db, subject -> loadSubject(subject));
        table = new AttendanceTable(db, this::copyRoster);

        contextBadge = new Label();
        contextBadge.getStyleClass().add("badge");

        VBox appBar = buildAppBar();

        BorderPane root = new BorderPane();
        root.setLeft(sidebar);
        root.setTop(appBar);
        root.setCenter(table);

        Scene scene = new Scene(root, 1480, 880);
        this.scene = scene;
        String css = dark ? "/dark.css" : "/light.css";
        scene.getStylesheets().add(getClass().getResource(css).toExternalForm());
        applyZoom();
        installZoomShortcuts(scene);
        stage.setScene(scene);

        if (currentSubject != null) {
            sidebar.select(currentSubject);
            table.setSubject(currentSubject);
            updateContextBadge();
        } else {
            updateContextBadge();
        }
    }

    private VBox buildAppBar() {
        Label title = new Label(Lang.get("app.title"));
        title.getStyleClass().add("app-title");

        Button language = new Button(Lang.get().name().equals("ZH") ? "EN" : "中文");
        language.getStyleClass().add("button");
        language.setOnAction(e -> {
            Lang.toggle();
            render();
        });

        Button theme = new Button(dark ? Lang.get("light") : Lang.get("dark"));
        theme.getStyleClass().add("button");
        theme.setOnAction(e -> {
            dark = !dark;
            render();
        });

        HBox left = new HBox(12, title, contextBadge);
        left.setAlignment(Pos.CENTER_LEFT);

        HBox right = new HBox(8, zoomOutButton(), zoomResetButton(), zoomInButton(),
                new Separator(javafx.geometry.Orientation.VERTICAL), language, theme);
        right.setAlignment(Pos.CENTER_RIGHT);

        HBox heading = new HBox(left, right);
        HBox.setHgrow(left, Priority.ALWAYS);
        heading.setAlignment(Pos.CENTER_LEFT);

        Button settings = tool(Lang.get("settings"), e -> openSettings());
        Button importRoster = tool(Lang.get("import.roster"), e -> importExcel(false));
        Button importBoth = tool(Lang.get("import.attendance"), e -> importExcel(true));
        Button template = tool(Lang.get("template"), e -> exportTemplate());
        Button export = tool(Lang.get("export"), e -> exportExcel());
        export.getStyleClass().add("primary");
        Button exportPdf = tool(Lang.get("export.pdf"), e -> exportPdf());
        Button print = tool(Lang.get("print"), e -> printPdf());
        Button stats = tool(Lang.get("stats"), e -> showStats());
        Button profiles = tool(Lang.get("profiles"), e -> showProfiles());

        FlowPane tools = new FlowPane(8, 8, settings,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                importRoster, importBoth, template, export, exportPdf, print,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                stats, profiles);
        tools.setAlignment(Pos.CENTER_LEFT);

        VBox bar = new VBox(8, heading, tools);
        bar.setPadding(new javafx.geometry.Insets(10, 14, 10, 14));
        bar.getStyleClass().add("toolbar");
        return bar;
    }

    private Button tool(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button button = new Button(text);
        button.getStyleClass().add("button");
        button.setOnAction(action);
        return button;
    }

    private Button zoomOutButton() {
        Button button = new Button("A\u2212");
        button.getStyleClass().add("button");
        button.setTooltip(new javafx.scene.control.Tooltip(Lang.get("zoom.out")));
        button.setOnAction(e -> changeZoom(-0.1));
        return button;
    }

    private Button zoomInButton() {
        Button button = new Button("A+");
        button.getStyleClass().add("button");
        button.setTooltip(new javafx.scene.control.Tooltip(Lang.get("zoom.in")));
        button.setOnAction(e -> changeZoom(0.1));
        return button;
    }

    private Button zoomResetButton() {
        Button button = new Button(Math.round(zoom * 100) + "%");
        button.getStyleClass().add("button");
        button.setTooltip(new javafx.scene.control.Tooltip(Lang.get("zoom.reset")));
        button.setOnAction(e -> {
            zoom = 1.0;
            applyZoom();
            button.setText("100%");
        });
        return button;
    }

    private void changeZoom(double delta) {
        zoom = Math.max(0.6, Math.min(2.0, zoom + delta));
        applyZoom();
    }

    private void applyZoom() {
        if (scene == null) {
            return;
        }
        scene.getRoot().setStyle("-fx-font-size: " + Math.round(13 * zoom) + "px;");
    }

    private void installZoomShortcuts(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!event.isControlDown() && !event.isShortcutDown()) {
                return;
            }
            switch (event.getCode()) {
                case EQUALS, PLUS, ADD -> {
                    changeZoom(0.1);
                    event.consume();
                }
                case MINUS, SUBTRACT -> {
                    changeZoom(-0.1);
                    event.consume();
                }
                case DIGIT0, NUMPAD0 -> {
                    zoom = 1.0;
                    applyZoom();
                    event.consume();
                }
                default -> {
                }
            }
        });
        scene.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                changeZoom(event.getDeltaY() > 0 ? 0.1 : -0.1);
                event.consume();
            }
        });
    }

    private void updateContextBadge() {
        if (contextBadge == null) {
            return;
        }
        if (currentSubject == null) {
            contextBadge.setText(Lang.get("no.subject"));
            return;
        }
        Semester semester = db.semesters().stream()
                .filter(s -> s.getId() == currentSubject.getSemesterId())
                .findFirst().orElse(null);
        String semesterName = semester == null ? "" : semester.getName() + " / ";
        contextBadge.setText(semesterName + currentSubject.getName());
    }

    // ------------------------------------------------------------------
    // Subject selection
    // ------------------------------------------------------------------

    private void loadSubject(Subject subject) {
        currentSubject = subject;
        if (table != null) {
            table.setSubject(subject);
        }
        updateContextBadge();
    }

    private void copyRoster(Subject target) {
        // Handled inside AttendanceTable, kept for the callback contract.
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    private void openSettings() {
        if (currentSubject == null) {
            return;
        }
        Sidebar.SubjectDraft draft = Sidebar.subjectDialog(Lang.get("settings"), currentSubject);
        if (draft == null) {
            return;
        }
        db.updateSubject(currentSubject.getId(), draft.name(), draft.weeks(), draft.lessons());
        Subject updated = db.subjects(currentSubject.getSemesterId()).stream()
                .filter(s -> s.getId() == currentSubject.getId())
                .findFirst().orElse(currentSubject);
        currentSubject = updated;
        table.subjectChanged(updated);
        sidebar.refresh();
        updateContextBadge();
    }

    // ------------------------------------------------------------------
    // Excel
    // ------------------------------------------------------------------

    private void importExcel(boolean withAttendance) {
        if (currentSubject == null) {
            alert(Lang.get("no.subject"));
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.get(withAttendance ? "import.attendance" : "import.roster"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        try {
            List<ExcelIO.RosterRow> rows = ExcelIO.read(file);
            if (rows.isEmpty()) {
                alert(Lang.get("empty.file"));
                return;
            }
            int added = 0;
            int existing = 0;
            for (ExcelIO.RosterRow row : rows) {
                if (db.addStudent(currentSubject.getId(), row.externalId(), row.name())) {
                    added++;
                } else {
                    existing++;
                }
            }
            if (withAttendance) {
                applyImportedAttendance(rows);
            }
            table.reload();
            updateContextBadge();
            alert(Lang.get("import.done") + "   " + String.format(Lang.get("import.merged"), added, existing));
        } catch (Exception e) {
            error(Lang.get("import.failed") + ": " + e.getMessage());
        }
    }

    private void applyImportedAttendance(List<ExcelIO.RosterRow> rows) {
        Map<String, Integer> idToDatabaseId = new HashMap<>();
        for (Student student : db.roster(currentSubject.getId())) {
            idToDatabaseId.put(student.getExternalId(), student.getDatabaseId());
        }
        for (ExcelIO.RosterRow row : rows) {
            Integer studentId = idToDatabaseId.get(row.externalId());
            if (studentId == null || row.statuses().isEmpty()) {
                continue;
            }
            for (Map.Entry<Long, Integer> entry : row.statuses().entrySet()) {
                long key = entry.getKey();
                int week = (int) (key >> 32);
                int lesson = (int) key;
                db.setStatus(currentSubject.getId(), studentId, week, lesson, entry.getValue());
            }
        }
    }

    private void exportExcel() {
        if (currentSubject == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.get("export"));
        chooser.setInitialFileName(currentSubject.getName() + ".xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            ExcelIO.export(file, currentSubject.getName(), sheetHeaders(), sheetLines());
            alert(Lang.get("export.done") + ": " + file.getAbsolutePath());
        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    private void exportTemplate() {
        if (currentSubject == null) {
            alert(Lang.get("no.subject"));
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.get("template"));
        chooser.setInitialFileName(currentSubject.getName() + "-template.xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            ExcelIO.export(file, currentSubject.getName(), sheetHeaders(), new ArrayList<>());
            alert(Lang.get("template.saved") + ": " + file.getAbsolutePath());
        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    private void exportPdf() {
        if (currentSubject == null) {
            alert(Lang.get("no.subject"));
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Lang.get("export.pdf"));
        chooser.setInitialFileName(currentSubject.getName() + ".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }
        try {
            PdfExporter.export(file, currentSubject.getName(), sheetHeaders(), sheetLines(), summaryLine());
            alert(Lang.get("pdf.saved") + ": " + file.getAbsolutePath());
        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    private void printPdf() {
        if (currentSubject == null) {
            alert(Lang.get("no.subject"));
            return;
        }
        try {
            Path temp = Files.createTempFile("attendance-", ".pdf");
            PdfExporter.export(temp.toFile(), currentSubject.getName(), sheetHeaders(), sheetLines(), summaryLine());
            open(temp.toFile());
            alert(Lang.get("pdf.opened") + ": " + temp);
        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    private void open(File file) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file);
                return;
            }
        } catch (Exception ignored) {
            // fall through to xdg-open
        }
        try {
            new ProcessBuilder("xdg-open", file.getAbsolutePath()).start();
        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    private void showStats() {
        if (currentSubject == null) {
            alert(Lang.get("no.subject"));
            return;
        }
        StatsDialog.show(stage, currentSubject.getName(), table.visibleStudents(),
                table.getWeeks(), table.getLessons());
    }

    private void showProfiles() {
        ProfileWindow.show(stage, db);
    }

    private List<String> sheetHeaders() {
        int weeks = table.getWeeks();
        int lessons = table.getLessons();
        List<String> headers = new ArrayList<>();
        headers.add(Lang.get("student.name"));
        headers.add(Lang.get("student.id"));
        for (int w = 1; w <= weeks; w++) {
            for (int l = 1; l <= lessons; l++) {
                headers.add("W" + w + "-L" + l);
            }
        }
        headers.add(Lang.get("attendance.rate"));
        headers.add(Lang.get("late.count"));
        return headers;
    }

    private List<String[]> sheetLines() {
        int weeks = table.getWeeks();
        int lessons = table.getLessons();
        int columns = 2 + weeks * lessons + 2;
        List<String[]> lines = new ArrayList<>();
        for (Student student : table.visibleStudents()) {
            String[] line = new String[columns];
            line[0] = student.getName();
            line[1] = student.getExternalId();
            int index = 2;
            for (int w = 1; w <= weeks; w++) {
                for (int l = 1; l <= lessons; l++) {
                    line[index++] = symbol(student.status(w - 1, l - 1));
                }
            }
            line[index++] = student.getRate() < 0 ? "-" : String.format("%.1f%%", student.getRate());
            line[index] = String.valueOf(student.getLate());
            lines.add(line);
        }
        return lines;
    }

    private String summaryLine() {
        int students = table.visibleStudents().size();
        return students + " " + Lang.get("students");
    }

    private void error(String message) {
        Alert dialog = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        dialog.setTitle(Lang.get("app.title"));
        dialog.setHeaderText(null);
        dialog.showAndWait();
    }

    private static String symbol(int status) {
        return switch (status) {
            case DatabaseManager.STATUS_PRESENT -> "P";
            case DatabaseManager.STATUS_ABSENT -> "A";
            case DatabaseManager.STATUS_LATE -> "L";
            default -> "";
        };
    }

    private void alert(String message) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        dialog.setTitle(Lang.get("app.title"));
        dialog.setHeaderText(null);
        dialog.showAndWait();
    }

    @Override
    public void stop() {
        if (db != null) {
            db.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    /** The packaged app stores its database outside the read only image. */
    private static File databaseFile() {
        String custom = System.getProperty("attendance.db");
        if (custom != null && !custom.isBlank()) {
            File file = new File(custom);
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            return file;
        }
        return new File("attendance.db");
    }
}
