package com.attendance.app.ui;

import com.attendance.app.database.DatabaseManager;
import com.attendance.app.i18n.Lang;
import com.attendance.app.model.Semester;
import com.attendance.app.model.Subject;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Left hand navigation: an unlimited tree of semesters and subjects.
 */
public class Sidebar extends VBox {

    /** Tree label plus the object it stands for, either a Semester or a Subject. */
    public record Entry(String label, Object payload) {
        @Override
        public String toString() {
            return label;
        }
    }

    public record SubjectDraft(String name, int weeks, int lessons) {
    }

    private final DatabaseManager db;
    private final TreeView<Entry> tree = new TreeView<>();
    private final Consumer<Subject> subjectSelected;
    private Subject selectedSubject;

    public Sidebar(DatabaseManager db, Consumer<Subject> subjectSelected) {
        this.db = db;
        this.subjectSelected = subjectSelected;
        getStyleClass().add("sidebar");
        setPrefWidth(230);
        setMinWidth(190);

        tree.setShowRoot(false);
        tree.setCellFactory(view -> new NodeCell());
        tree.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, value) -> {
            if (value != null && value.getValue() != null && value.getValue().payload() instanceof Subject subject) {
                selectedSubject = subject;
                subjectSelected.accept(subject);
            }
        });

        Button newSemester = new Button("+ " + Lang.get("new.semester"));
        newSemester.getStyleClass().addAll("button", "ghost");
        newSemester.setMaxWidth(Double.MAX_VALUE);
        newSemester.setOnAction(e -> createSemester());

        Button newSubject = new Button("+ " + Lang.get("new.subject"));
        newSubject.getStyleClass().addAll("button", "ghost");
        newSubject.setMaxWidth(Double.MAX_VALUE);
        newSubject.setOnAction(e -> createSubject(currentSemesterId()));

        VBox footer = new VBox(4, newSemester, newSubject);
        footer.setPadding(new Insets(8, 4, 0, 4));

        getChildren().addAll(tree, footer);
        VBox.setVgrow(tree, Priority.ALWAYS);

        refresh();
    }

    public void refresh() {
        TreeItem<Entry> root = new TreeItem<>();
        TreeItem<Entry> selectThis = null;
        for (Semester semester : db.semesters()) {
            TreeItem<Entry> semesterItem = new TreeItem<>(new Entry(semester.getName(), semester));
            for (Subject subject : db.subjects(semester.getId())) {
                TreeItem<Entry> subjectItem = new TreeItem<>(new Entry(subject.getName(), subject));
                semesterItem.getChildren().add(subjectItem);
                if (selectedSubject != null && subject.getId() == selectedSubject.getId()) {
                    selectThis = subjectItem;
                }
            }
            root.getChildren().add(semesterItem);
        }
        tree.setRoot(root);
        tree.getSelectionModel().clearSelection();
        if (selectThis != null) {
            selectThis.setExpanded(true);
            tree.getSelectionModel().select(selectThis);
        }
    }

    public void select(Subject subject) {
        selectedSubject = subject;
        refresh();
    }

    public void selectSemester(Semester semester) {
        for (TreeItem<Entry> item : tree.getRoot().getChildren()) {
            if (item.getValue() != null && item.getValue().payload() instanceof Semester s
                    && s.getId() == semester.getId()) {
                tree.getSelectionModel().select(item);
                return;
            }
        }
    }

    private int currentSemesterId() {
        Semester semester = selectedSemester();
        return semester == null ? -1 : semester.getId();
    }

    private Semester selectedSemester() {
        TreeItem<Entry> item = tree.getSelectionModel().getSelectedItem();
        if (item != null && item.getValue() != null) {
            if (item.getValue().payload() instanceof Subject subject) {
                return db.semesters().stream()
                        .filter(s -> s.getId() == subject.getSemesterId())
                        .findFirst().orElse(null);
            }
            if (item.getValue().payload() instanceof Semester semester) {
                return semester;
            }
        }
        if (selectedSubject != null) {
            int semesterId = selectedSubject.getSemesterId();
            return db.semesters().stream().filter(s -> s.getId() == semesterId).findFirst().orElse(null);
        }
        List<Semester> all = db.semesters();
        return all.isEmpty() ? null : all.get(0);
    }

    private void createSemester() {
        String name = prompt(Lang.get("new.semester"), Lang.get("semester"), "");
        if (name == null) return;
        db.createSemester(name);
        refresh();
    }

    private void createSubject(int semesterId) {
        if (semesterId < 0) {
            createSemester();
            semesterId = currentSemesterId();
            if (semesterId < 0) return;
        }
        SubjectDraft draft = subjectDialog(Lang.get("new.subject"), null);
        if (draft != null) {
            db.createSubject(semesterId, draft.name(), draft.weeks(), draft.lessons());
            refresh();
        }
    }

    // ------------------------------------------------------------------
    // Dialogs
    // ------------------------------------------------------------------

    public static String prompt(String title, String label, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(label);
        TextField field = dialog.getEditor();
        field.selectAll();
        return dialog.showAndWait().filter(s -> !s.isBlank()).orElse(null);
    }

    public static SubjectDraft subjectDialog(String title, Subject current) {
        Dialog<SubjectDraft> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField name = new TextField(current == null ? "" : current.getName());
        name.setPromptText(Lang.get("name"));

        Spinner<Integer> weeks = new Spinner<>(1, 60, current == null ? 8 : current.getWeekCount());
        weeks.setEditable(true);
        weeks.setPrefWidth(100);

        Spinner<Integer> lessons = new Spinner<>(1, 10, current == null ? 2 : current.getLessonCount());
        lessons.setEditable(true);
        lessons.setPrefWidth(100);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label(Lang.get("name")), name);
        grid.addRow(1, new Label(Lang.get("week.count")), weeks);
        grid.addRow(2, new Label(Lang.get("lesson.count")), lessons);
        GridPane.setHgrow(name, Priority.ALWAYS);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new SubjectDraft(name.getText(), weeks.getValue(), lessons.getValue())
                : null);
        return dialog.showAndWait().orElse(null);
    }

    public static boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(Lang.get("app.title"));
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    /** Lists every subject in the app and returns the chosen one, or null. */
    public static Subject chooseSubject(DatabaseManager db, String title, Subject exclude) {
        List<Subject> choices = new java.util.ArrayList<>();
        List<String> labels = new java.util.ArrayList<>();
        for (Semester semester : db.semesters()) {
            for (Subject subject : db.subjects(semester.getId())) {
                if (exclude != null && subject.getId() == exclude.getId()) continue;
                choices.add(subject);
                labels.add(semester.getName() + " / " + subject.getName());
            }
        }
        if (choices.isEmpty()) {
            return null;
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(labels.get(0), labels);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(title);
        return dialog.showAndWait().map(labels::indexOf).map(choices::get).orElse(null);
    }

    // ------------------------------------------------------------------
    // Cells
    // ------------------------------------------------------------------

    private final class NodeCell extends TreeCell<Entry> {

        @Override
        protected void updateItem(Entry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setContextMenu(null);
                return;
            }
            setText(item.label());
            setContextMenu(buildMenu(item.payload()));
        }
    }

    private ContextMenu buildMenu(Object payload) {
        ContextMenu menu = new ContextMenu();
        if (payload instanceof Semester semester) {
            MenuItem addSubject = new MenuItem("+ " + Lang.get("new.subject"));
            addSubject.setOnAction(e -> createSubject(semester.getId()));
            MenuItem rename = new MenuItem(Lang.get("rename"));
            rename.setOnAction(e -> {
                String name = prompt(Lang.get("rename"), Lang.get("semester"), semester.getName());
                if (name != null) db.renameSemester(semester.getId(), name);
                refresh();
            });
            MenuItem delete = new MenuItem(Lang.get("delete"));
            delete.setOnAction(e -> {
                if (confirm(Lang.get("confirm.delete.semester"))) {
                    db.deleteSemester(semester.getId());
                    selectedSubject = null;
                    refresh();
                }
            });
            menu.getItems().addAll(addSubject, rename, delete);
            return menu;
        }
        if (payload instanceof Subject subject) {
            MenuItem rename = new MenuItem(Lang.get("rename"));
            rename.setOnAction(e -> {
                SubjectDraft draft = subjectDialog(Lang.get("rename"), subject);
                if (draft != null) {
                    db.updateSubject(subject.getId(), draft.name(), draft.weeks(), draft.lessons());
                    refresh();
                }
            });
            MenuItem delete = new MenuItem(Lang.get("delete"));
            delete.setOnAction(e -> {
                if (confirm(Lang.get("confirm.delete.subject"))) {
                    db.deleteSubject(subject.getId());
                    selectedSubject = null;
                    refresh();
                }
            });
            menu.getItems().addAll(rename, delete);
            return menu;
        }
        return null;
    }
}
