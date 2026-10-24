package com.attendance.app.ui;

import com.attendance.app.database.DatabaseManager;
import com.attendance.app.i18n.Lang;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;

/** A school wide table: every student with their totals across all subjects. */
public final class ProfileWindow {

    private ProfileWindow() {
    }

    public static void show(Window owner, DatabaseManager db) {
        List<DatabaseManager.StudentProfile> profiles = db.profiles();
        ObservableList<DatabaseManager.StudentProfile> data = FXCollections.observableArrayList(profiles);
        FilteredList<DatabaseManager.StudentProfile> filtered = new FilteredList<>(data, p -> true);

        TableView<DatabaseManager.StudentProfile> table = new TableView<>(filtered);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<DatabaseManager.StudentProfile, String> nameColumn = new TableColumn<>(Lang.get("name"));
        nameColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().name()));

        TableColumn<DatabaseManager.StudentProfile, String> idColumn = new TableColumn<>(Lang.get("student.id"));
        idColumn.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().externalId()));

        TableColumn<DatabaseManager.StudentProfile, Number> subjectColumn = new TableColumn<>(Lang.get("profile.subjects"));
        subjectColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().subjects()));

        TableColumn<DatabaseManager.StudentProfile, Number> markedColumn = new TableColumn<>(Lang.get("marked"));
        markedColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().marked()));

        TableColumn<DatabaseManager.StudentProfile, String> rateColumn = new TableColumn<>(Lang.get("profile.rate"));
        rateColumn.setCellValueFactory(c -> {
            double rate = c.getValue().rate();
            return new ReadOnlyStringWrapper(rate < 0 ? "-" : String.format("%.1f%%", rate));
        });

        TableColumn<DatabaseManager.StudentProfile, Number> lateColumn = new TableColumn<>(Lang.get("late.count"));
        lateColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().late()));

        table.getColumns().addAll(List.of(nameColumn, idColumn, subjectColumn, markedColumn, rateColumn, lateColumn));

        TextField search = new TextField();
        search.setPromptText(Lang.get("search"));
        search.textProperty().addListener((obs, o, n) -> {
            String needle = n == null ? "" : n.trim().toLowerCase();
            filtered.setPredicate(p -> needle.isEmpty()
                    || p.name().toLowerCase().contains(needle)
                    || p.externalId().toLowerCase().contains(needle));
        });

        Label caption = new Label(Lang.get("profiles") + "  ·  " + profiles.size() + " " + Lang.get("students"));
        caption.getStyleClass().add("stat-caption");

        VBox root = new VBox(12, caption, search, table);
        root.setPadding(new Insets(16));
        VBox.setVgrow(table, Priority.ALWAYS);

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(Lang.get("profiles"));
        javafx.scene.Scene scene = new javafx.scene.Scene(root, 760, 640);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.showAndWait();
    }
}