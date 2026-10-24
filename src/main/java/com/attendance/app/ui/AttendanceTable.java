package com.attendance.app.ui;

import com.attendance.app.database.DatabaseManager;
import com.attendance.app.i18n.Lang;
import com.attendance.app.model.Semester;
import com.attendance.app.model.Student;
import com.attendance.app.model.Subject;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.geometry.Side;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The attendance grid: one row per student, one column group per week,
 * one sub column per lesson inside that week.
 */
public class AttendanceTable extends VBox {

    public interface Callbacks {
        void copyRosterFrom(Subject target);
    }

    private static final String[] STATUS_STYLE = {
            null,
            "-fx-background-color: #22c55e; -fx-text-fill: #ffffff;",
            "-fx-background-color: #ef4444; -fx-text-fill: #ffffff;",
            "-fx-background-color: #f59e0b; -fx-text-fill: #1f2937;"
    };

    private final DatabaseManager db;
    private final Callbacks callbacks;
    private final ObservableList<Student> students = FXCollections.observableArrayList();
    private final FilteredList<Student> filtered = new FilteredList<>(students);
    private final SortedList<Student> sorted = new SortedList<>(filtered);
    private final TableView<Student> table = new TableView<>();
    private final Map<TableColumn<Student, ?>, int[]> cellCoordinates = new HashMap<>();
    private final Label summary = new Label();
    private final TextField search = new TextField();

    private Subject subject;
    private int weeks;
    private int lessons;

    public AttendanceTable(DatabaseManager db, Callbacks callbacks) {
        this.db = db;
        this.callbacks = callbacks;
        setSpacing(0);

        VBox topBar = buildTopBar();
        table.setItems(sorted);

        table.setEditable(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label(Lang.get("no.subject")));
        installKeyboardShortcuts();

        summary.getStyleClass().addAll("status-bar");
        summary.setPadding(new Insets(8, 14, 8, 14));

        getChildren().addAll(topBar, table, summary);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setMaxHeight(Double.MAX_VALUE);
    }

    // ------------------------------------------------------------------
    // Top bar: search + batch actions
    // ------------------------------------------------------------------

    private VBox buildTopBar() {
        search.setPromptText(Lang.get("search"));
        search.setPrefWidth(200);
        search.textProperty().addListener((obs, oldValue, newValue) -> {
            String needle = newValue == null ? "" : newValue.trim().toLowerCase();
            filtered.setPredicate(s -> needle.isEmpty()
                    || s.getName().toLowerCase().contains(needle)
                    || s.getExternalId().toLowerCase().contains(needle));
            updateSummary();
        });

        TextField nameField = new TextField();
        nameField.setPromptText(Lang.get("student.name"));
        nameField.setPrefWidth(150);
        TextField idField = new TextField();
        idField.setPromptText(Lang.get("student.id.hint"));
        idField.setPrefWidth(130);
        Button add = new Button(Lang.get("add"));
        add.getStyleClass().addAll("button", "primary");
        Runnable addStudent = () -> {
            if (subject == null) {
                info(Lang.get("no.subject"));
                return;
            }
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            String id = idField.getText() == null ? "" : idField.getText().trim();
            if (name.isEmpty() || id.isEmpty()) {
                return;
            }
            if (!id.matches("\\d+")) {
                showIdError(Lang.get("student.id.hint"));
                return;
            }
            if (!db.addStudent(subject.getId(), id, name)) {
                showIdError(Lang.get("duplicate.id"));
                return;
            }
            nameField.clear();
            idField.clear();
            nameField.requestFocus();
            reload();
        };
        add.setOnAction(e -> addStudent.run());
        idField.setOnAction(e -> addStudent.run());
        nameField.setOnAction(e -> idField.requestFocus());

        Button copy = new Button(Lang.get("copy.roster"));
        copy.getStyleClass().add("button");
        copy.setOnAction(e -> copyRoster());

        HBox addBar = new HBox(8, nameField, idField, add, new Separator(Orientation.VERTICAL), search, copy);
        addBar.setAlignment(Pos.CENTER_LEFT);
        addBar.setPadding(new Insets(10, 14, 6, 14));

        Button presentAll = new Button(Lang.get("mark.all.present"));
        presentAll.getStyleClass().add("button");
        presentAll.setOnAction(e -> batch(DatabaseManager.STATUS_PRESENT));

        Button clearColumn = new Button(Lang.get("clear.column"));
        clearColumn.getStyleClass().add("button");
        clearColumn.setOnAction(e -> batch(DatabaseManager.STATUS_NONE));

        Button selectAll = new Button(Lang.get("select.all"));
        selectAll.getStyleClass().add("button");
        selectAll.setOnAction(e -> selectAll(true));

        Button clearSelection = new Button(Lang.get("clear.selection"));
        clearSelection.getStyleClass().add("button");
        clearSelection.setOnAction(e -> selectAll(false));

        Button deleteSelected = new Button(Lang.get("delete.selected"));
        deleteSelected.getStyleClass().addAll("button", "danger");
        deleteSelected.setOnAction(e -> deleteSelected());

        Label hint = new Label(Lang.get("hint.focused.column"));
        hint.getStyleClass().add("hint-label");

        HBox actionBar = new HBox(10, presentAll, clearColumn,
                new Separator(Orientation.VERTICAL), selectAll, clearSelection, deleteSelected, hint);
        actionBar.setAlignment(Pos.CENTER_LEFT);
        actionBar.setPadding(new Insets(0, 14, 10, 14));

        VBox bar = new VBox(0, addBar, actionBar);
        bar.getStyleClass().add("toolbar");
        return bar;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    public void setSubject(Subject subject) {
        this.subject = subject;
        this.weeks = subject.getWeekCount();
        this.lessons = subject.getLessonCount();
        reload();
    }

    /** Re-reads the roster and attendance, keeping the current subject. */
    public void reload() {
        if (subject == null) {
            students.clear();
            table.getColumns().clear();
            table.setPlaceholder(new Label(Lang.get("no.subject")));
            updateSummary();
            return;
        }
        weeks = subject.getWeekCount();
        lessons = subject.getLessonCount();

        List<Student> roster = db.roster(subject.getId());
        int[][][] grid = db.attendanceGrid(subject.getId(), roster, weeks, lessons);
        students.clear();
        for (int i = 0; i < roster.size(); i++) {
            Student student = roster.get(i);
            student.loadStatuses(grid, i);
            students.add(student);
        }
        rebuildColumns();
        table.setPlaceholder(new Label(students.isEmpty() ? Lang.get("no.students") : Lang.get("no.subject")));
        updateSummary();
    }

    public Subject getSubject() {
        return subject;
    }

    /** The rows exactly as the teacher sees them, after search and sorting. */
    public java.util.List<Student> visibleStudents() {
        return java.util.List.copyOf(sorted);
    }

    public int getWeeks() {
        return weeks;
    }

    public int getLessons() {
        return lessons;
    }

    /** Re-reads one subject after its settings changed. */
    public void subjectChanged(Subject updated) {
        this.subject = updated;
        reload();
    }

    public void filter(String query) {
        search.setText(query);
    }

    // ------------------------------------------------------------------
    // Columns
    // ------------------------------------------------------------------

    private void rebuildColumns() {
        cellCoordinates.clear();
        table.getColumns().clear();
        if (subject == null) return;

        TableColumn<Student, Boolean> selectColumn = new TableColumn<>("");
        selectColumn.setCellValueFactory(d -> d.getValue().selectedProperty());
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));
        selectColumn.setPrefWidth(40);
        selectColumn.setMinWidth(40);
        selectColumn.setSortable(false);
        selectColumn.setEditable(true);
        table.getColumns().add(selectColumn);

        TableColumn<Student, String> nameColumn = new TableColumn<>(Lang.get("student.name"));
        nameColumn.setCellValueFactory(d -> d.getValue().nameProperty());
        nameColumn.setCellFactory(c -> new TextCell("name-cell"));
        nameColumn.setPrefWidth(160);
        nameColumn.setMinWidth(110);
        nameColumn.setOnEditCommit(e -> {
            Student student = e.getRowValue();
            String value = e.getNewValue() == null ? "" : e.getNewValue().trim();
            if (value.isEmpty()) {
                student.nameProperty().set(e.getOldValue());
                return;
            }
            db.renameStudent(student.getDatabaseId(), value);
            student.nameProperty().set(value);
            updateSummary();
        });

        TableColumn<Student, String> idColumn = new TableColumn<>(Lang.get("student.id"));
        idColumn.setCellValueFactory(d -> d.getValue().idProperty());
        idColumn.setCellFactory(c -> new TextCell("id-cell"));
        idColumn.setPrefWidth(130);
        idColumn.setMinWidth(90);
        idColumn.setOnEditCommit(e -> {
            Student student = e.getRowValue();
            String value = e.getNewValue() == null ? "" : e.getNewValue().trim();
            if (!value.matches("\\d+")) {
                showIdError(Lang.get("student.id.hint"));
                student.idProperty().set(e.getOldValue());
                return;
            }
            if (!db.updateExternalId(student.getDatabaseId(), value)) {
                showIdError(Lang.get("duplicate.id"));
                student.idProperty().set(e.getOldValue());
                return;
            }
            student.idProperty().set(value);
        });

        table.getColumns().add(nameColumn);
        table.getColumns().add(idColumn);

        String weekPrefix = Lang.get("week.label");
        String weekSuffix = Lang.get("week.suffix");
        String lessonPrefix = Lang.get("lesson.label");

        for (int w = 0; w < weeks; w++) {
            final int week = w;
            TableColumn<Student, String> weekColumn = new TableColumn<>(
                    weekPrefix + (w + 1) + (weekSuffix.isEmpty() ? "" : weekSuffix));
            weekColumn.getStyleClass().add("group-head");
            weekColumn.setSortable(false);
            weekColumn.setMinWidth(46 * lessons);

            for (int l = 0; l < lessons; l++) {
                final int lesson = l;
                TableColumn<Student, String> lessonColumn = new TableColumn<>(lessonPrefix + (l + 1));
                lessonColumn.setPrefWidth(48);
                lessonColumn.setMinWidth(44);
                lessonColumn.setSortable(true);
                lessonColumn.setCellValueFactory(d -> new ReadOnlyStringWrapper(
                        String.valueOf(d.getValue().status(week, lesson))));
                lessonColumn.setCellFactory(column -> new StatusCell(week, lesson));
                cellCoordinates.put(lessonColumn, new int[]{week, lesson});
                weekColumn.getColumns().add(lessonColumn);
            }
            table.getColumns().add(weekColumn);
        }

        TableColumn<Student, Number> rateColumn = new TableColumn<>(Lang.get("attendance.rate"));
        rateColumn.setPrefWidth(90);
        rateColumn.setMinWidth(80);
        rateColumn.setCellValueFactory(d -> d.getValue().rateProperty());
        rateColumn.setCellFactory(column -> new RateCell());

        TableColumn<Student, Number> lateColumn = new TableColumn<>(Lang.get("late.count"));
        lateColumn.setPrefWidth(70);
        lateColumn.setMinWidth(60);
        lateColumn.setCellValueFactory(d -> d.getValue().lateProperty());

        table.getColumns().add(rateColumn);
        table.getColumns().add(lateColumn);

        TableColumn<Student, Void> actionsColumn = new TableColumn<>(Lang.get("actions"));
        actionsColumn.setPrefWidth(64);
        actionsColumn.setMinWidth(56);
        actionsColumn.setSortable(false);
        actionsColumn.setCellFactory(c -> new ActionCell());
        table.getColumns().add(actionsColumn);
    }

    private void showIdError(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
        alert.setTitle(Lang.get("app.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ------------------------------------------------------------------
    // Cells
    // ------------------------------------------------------------------

    private final class StatusCell extends TableCell<Student, String> {
        private final int week;
        private final int lesson;

        private StatusCell(int week, int lesson) {
            this.week = week;
            this.lesson = lesson;
            setOnMouseClicked(this::onMouse);
        }

        private void onMouse(javafx.scene.input.MouseEvent event) {
            Student student = getTableRow() == null ? null : getTableRow().getItem();
            if (student == null || event.getButton() != MouseButton.PRIMARY && event.getButton() != MouseButton.SECONDARY) {
                return;
            }
            if (event.getButton() == MouseButton.SECONDARY) {
                int row = getIndex();
                if (row >= 0 && row < sorted.size()) {
                    table.getSelectionModel().clearAndSelect(row, this.getTableColumn());
                    table.getFocusModel().focus(row, this.getTableColumn());
                }
                apply(student, DatabaseManager.STATUS_NONE);
                event.consume();
                return;
            }
            int current = student.status(week, lesson);
            int next = switch (current) {
                case DatabaseManager.STATUS_NONE -> DatabaseManager.STATUS_PRESENT;
                case DatabaseManager.STATUS_PRESENT -> DatabaseManager.STATUS_ABSENT;
                case DatabaseManager.STATUS_ABSENT -> DatabaseManager.STATUS_LATE;
                default -> DatabaseManager.STATUS_NONE;
            };
            apply(student, next);
            event.consume();
        }

        private void apply(Student student, int status) {
            student.setStatus(week, lesson, status);
            db.setStatus(subject.getId(), student.getDatabaseId(), week + 1, lesson + 1, status);
            table.refresh();
            updateSummary();
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            Student student = empty || getTableRow() == null ? null : getTableRow().getItem();
            if (student == null) {
                setText(null);
                setStyle("");
                return;
            }
            int status = student.status(week, lesson);
            setAlignment(javafx.geometry.Pos.CENTER);
            switch (status) {
                case DatabaseManager.STATUS_PRESENT -> {
                    setText("\u2705");
                    setStyle(STATUS_STYLE[1]);
                }
                case DatabaseManager.STATUS_ABSENT -> {
                    setText("\u274c");
                    setStyle(STATUS_STYLE[2]);
                }
                case DatabaseManager.STATUS_LATE -> {
                    setText("\ud83c\udfc3");
                    setStyle(STATUS_STYLE[3]);
                }
                default -> {
                    setText("");
                    setStyle("-fx-background-color: transparent; -fx-text-fill: transparent;");
                }
            }
        }
    }

    private final class TextCell extends TableCell<Student, String> {
        private final String styleClass;

        private TextCell(String styleClass) {
            this.styleClass = styleClass;
            getStyleClass().add(styleClass);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setText(null);
            } else {
                setText(item);
            }
        }
    }

    private final class RateCell extends TableCell<Student, Number> {
        @Override
        protected void updateItem(Number item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("rate-good", "rate-bad", "rate-none");
            Student student = empty || getTableRow() == null ? null : getTableRow().getItem();
            if (student == null || student.getRate() < 0) {
                setText("-");
                getStyleClass().add("rate-none");
                return;
            }
            setText(String.format("%.1f%%", student.getRate()));
            getStyleClass().add(student.getRate() < 60 ? "rate-bad" : "rate-good");
        }
    }

    /** The per row three dot menu with edit and delete actions. */
    private final class ActionCell extends TableCell<Student, Void> {
        private final Button dots = new Button("\u22ee");
        private final ContextMenu menu = new ContextMenu();
        private final MenuItem editName = new MenuItem(Lang.get("edit.name"));
        private final MenuItem editId = new MenuItem(Lang.get("edit.id"));
        private final MenuItem delete = new MenuItem(Lang.get("delete"));

        private ActionCell() {
            dots.getStyleClass().add("dots-button");
            dots.setOnAction(e -> menu.show(dots, Side.RIGHT, 0, 0));
            getStyleClass().add("action-cell");
            menu.getItems().addAll(editName, editId, delete);
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            Student student = empty || getTableRow() == null ? null : getTableRow().getItem();
            if (student == null) {
                setGraphic(null);
                return;
            }
            editName.setOnAction(e -> editName(student));
            editId.setOnAction(e -> editId(student));
            delete.setOnAction(e -> deleteStudent(student));
            setGraphic(dots);
        }
    }

    // ------------------------------------------------------------------
    // Student actions
    // ------------------------------------------------------------------

    private void editName(Student student) {
        TextInputDialog dialog = new TextInputDialog(student.getName());
        dialog.setTitle(Lang.get("edit.name"));
        dialog.setHeaderText(null);
        dialog.setContentText(Lang.get("name"));
        dialog.showAndWait().ifPresent(value -> {
            String name = value.trim();
            if (name.isEmpty()) {
                return;
            }
            db.renameStudent(student.getDatabaseId(), name);
            reload();
        });
    }

    private void editId(Student student) {
        TextInputDialog dialog = new TextInputDialog(student.getExternalId());
        dialog.setTitle(Lang.get("edit.id"));
        dialog.setHeaderText(null);
        dialog.setContentText(Lang.get("student.id"));
        dialog.showAndWait().ifPresent(value -> {
            String id = value.trim();
            if (!id.matches("\\d+")) {
                showIdError(Lang.get("student.id.hint"));
                return;
            }
            if (!db.updateExternalId(student.getDatabaseId(), id)) {
                showIdError(Lang.get("duplicate.id"));
                return;
            }
            reload();
        });
    }

    private void deleteStudent(Student student) {
        if (!confirm(String.format(Lang.get("confirm.delete.student"), student.getName()))) {
            return;
        }
        db.removeStudentFromSubject(subject.getId(), student.getDatabaseId());
        reload();
    }

    private void deleteSelected() {
        List<Student> selected = checkedStudents();
        if (selected.isEmpty()) {
            info(Lang.get("select.none"));
            return;
        }
        if (!confirm(String.format(Lang.get("confirm.delete.selected"), selected.size()))) {
            return;
        }
        for (Student student : selected) {
            db.removeStudentFromSubject(subject.getId(), student.getDatabaseId());
        }
        reload();
    }

    private void selectAll(boolean value) {
        for (Student student : filtered) {
            student.setSelected(value);
        }
        if (!value) {
            table.getSelectionModel().clearSelection();
        }
        table.refresh();
    }

    private List<Student> checkedStudents() {
        List<Student> result = new java.util.ArrayList<>();
        for (Student student : filtered) {
            if (student.isSelected()) {
                result.add(student);
            }
        }
        return result;
    }

    private boolean confirm(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION, message,
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        alert.setTitle(Lang.get("app.title"));
        alert.setHeaderText(null);
        return alert.showAndWait().filter(b -> b == javafx.scene.control.ButtonType.OK).isPresent();
    }

    // ------------------------------------------------------------------
    // Batch actions
    // ------------------------------------------------------------------

    private void batch(int status) {
        TablePosition<Student, ?> position = table.getFocusModel().getFocusedCell();
        if (position == null) {
            return;
        }
        int[] coordinate = findCoordinate(position.getColumn());
        if (coordinate == null) {
            return;
        }
        List<Student> targets = checkedStudents();
        if (targets.isEmpty()) {
            if (table.getSelectionModel().getSelectedItems().size() > 1) {
                targets = List.copyOf(table.getSelectionModel().getSelectedItems());
            } else {
                targets = List.copyOf(filtered);
            }
        }
        for (Student student : targets) {
            if (status == DatabaseManager.STATUS_PRESENT && student.status(coordinate[0], coordinate[1]) == DatabaseManager.STATUS_PRESENT) {
                continue;
            }
            student.setStatus(coordinate[0], coordinate[1], status);
            db.setStatus(subject.getId(), student.getDatabaseId(), coordinate[0] + 1, coordinate[1] + 1, status);
        }
        table.refresh();
        updateSummary();
    }

    private int[] findCoordinate(int columnIndex) {
        if (columnIndex < 0 || columnIndex >= table.getColumns().size()) {
            return null;
        }
        TableColumn<Student, ?> column = table.getColumns().get(columnIndex);
        if (cellCoordinates.containsKey(column)) {
            return cellCoordinates.get(column);
        }
        for (Object child : column.getColumns()) {
            @SuppressWarnings("unchecked")
            TableColumn<Student, ?> childColumn = (TableColumn<Student, ?>) child;
            int[] coordinate = cellCoordinates.get(childColumn);
            if (coordinate != null) {
                return coordinate;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Keyboard
    // ------------------------------------------------------------------

    private void installKeyboardShortcuts() {
        table.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            TablePosition<Student, ?> position = table.getFocusModel().getFocusedCell();
            if (position == null) {
                return;
            }
            int[] coordinate = findCoordinate(position.getColumn());
            if (coordinate == null) {
                return;
            }
            if (event.getCode() == KeyCode.SPACE) {
                Student student = table.getItems().get(position.getRow());
                int current = student.status(coordinate[0], coordinate[1]);
                int next = switch (current) {
                    case DatabaseManager.STATUS_NONE -> DatabaseManager.STATUS_PRESENT;
                    case DatabaseManager.STATUS_PRESENT -> DatabaseManager.STATUS_ABSENT;
                    case DatabaseManager.STATUS_ABSENT -> DatabaseManager.STATUS_LATE;
                    default -> DatabaseManager.STATUS_NONE;
                };
                student.setStatus(coordinate[0], coordinate[1], next);
                db.setStatus(subject.getId(), student.getDatabaseId(), coordinate[0] + 1, coordinate[1] + 1, next);
                table.refresh();
                updateSummary();
                event.consume();
            } else if (event.getCode() == KeyCode.DELETE) {
                Student student = table.getItems().get(position.getRow());
                student.setStatus(coordinate[0], coordinate[1], DatabaseManager.STATUS_NONE);
                db.setStatus(subject.getId(), student.getDatabaseId(), coordinate[0] + 1, coordinate[1] + 1,
                        DatabaseManager.STATUS_NONE);
                table.refresh();
                updateSummary();
                event.consume();
            }
        });
    }

    // ------------------------------------------------------------------
    // Roster helpers
    // ------------------------------------------------------------------

    private void copyRoster() {
        if (subject == null) {
            return;
        }
        java.util.List<Subject> choices = new java.util.ArrayList<>();
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (Semester semester : db.semesters()) {
            for (Subject other : db.subjects(semester.getId())) {
                if (other.getId() == subject.getId()) {
                    continue;
                }
                choices.add(other);
                labels.add(semester.getName() + " / " + other.getName());
            }
        }
        if (choices.isEmpty()) {
            info(Lang.get("nothing.to.copy"));
            return;
        }
        javafx.scene.control.ChoiceDialog<String> dialog =
                new javafx.scene.control.ChoiceDialog<>(labels.get(0), labels);
        dialog.setTitle(Lang.get("copy.roster"));
        dialog.setHeaderText(null);
        dialog.setContentText(Lang.get("copy.roster"));
        dialog.showAndWait().ifPresent(label -> {
            int index = labels.indexOf(label);
            if (index >= 0) {
                int added = db.copyRoster(choices.get(index).getId(), subject.getId());
                reload();
                info(Lang.get("roster.copied") + " " + added);
            }
        });
    }

    private void info(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(Lang.get("app.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ------------------------------------------------------------------
    // Summary
    // ------------------------------------------------------------------

    private void updateSummary() {
        if (subject == null) {
            summary.setText(Lang.get("no.subject"));
            return;
        }
        int cells = 0;
        double rateSum = 0;
        int rated = 0;
        int lateSum = 0;
        for (Student student : filtered) {
            cells += student.getMarked();
            lateSum += student.getLate();
            if (student.getRate() >= 0) {
                rateSum += student.getRate();
                rated++;
            }
        }
        String average = rated == 0 ? "-" : String.format("%.1f%%", rateSum / rated);
        summary.setText(String.format("%s %d    %s %d    %s %s    %s %d    ",
                Lang.get("students"), filtered.size(),
                Lang.get("marked"), cells,
                Lang.get("attendance.rate"), average,
                Lang.get("late.count"), lateSum)
                + "   " + Lang.get("hint.click"));
        summary.setTooltip(new Tooltip(Lang.get("hint.click")));
    }

}
