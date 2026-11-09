package com.attendance.app.ui;

import com.attendance.app.database.DatabaseManager;
import com.attendance.app.i18n.Lang;
import com.attendance.app.model.Student;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;

/** Per subject statistics: totals plus a weekly bar chart and a share pie. */
public final class StatsDialog {

    private StatsDialog() {
    }

    public static void show(Window owner, String subjectName, List<Student> students,
                            int weeks, int lessons) {
        int marked = 0;
        int present = 0;
        int absent = 0;
        int late = 0;
        int perfect = 0;
        double rateSum = 0;
        int rateCount = 0;
        int[] weekPresent = new int[weeks + 1];
        int[] weekAbsent = new int[weeks + 1];
        int[] weekLate = new int[weeks + 1];

        for (Student student : students) {
            for (int w = 0; w < weeks; w++) {
                for (int l = 0; l < lessons; l++) {
                    switch (student.status(w, l)) {
                        case DatabaseManager.STATUS_PRESENT -> {
                            present++;
                            marked++;
                            weekPresent[w + 1]++;
                        }
                        case DatabaseManager.STATUS_ABSENT -> {
                            absent++;
                            marked++;
                            weekAbsent[w + 1]++;
                        }
                        case DatabaseManager.STATUS_LATE -> {
                            late++;
                            marked++;
                            weekLate[w + 1]++;
                        }
                        default -> {
                        }
                    }
                }
            }
            if (student.getRate() >= 0) {
                rateSum += student.getRate();
                rateCount++;
                if (student.getRate() == 100) {
                    perfect++;
                }
            }
        }
        double average = rateCount == 0 ? -1 : rateSum / rateCount;

        HBox cards = new HBox(10,
                card(Lang.get("stats.students"), String.valueOf(students.size())),
                card(Lang.get("stats.marked"), String.valueOf(marked)),
                card(Lang.get("stats.average"), average < 0 ? "-" : String.format("%.1f%%", average)),
                card(Lang.get("stats.perfect"), String.valueOf(perfect)),
                card(Lang.get("stats.late"), String.valueOf(late)));
        cards.setAlignment(Pos.CENTER_LEFT);

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel(Lang.get("week.count"));
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> bar = new BarChart<>(xAxis, yAxis);
        bar.setTitle(Lang.get("stats.weeks"));
        bar.setLegendVisible(true);
        XYChart.Series<String, Number> presentSeries = new XYChart.Series<>();
        presentSeries.setName(Lang.get("present"));
        XYChart.Series<String, Number> absentSeries = new XYChart.Series<>();
        absentSeries.setName(Lang.get("absent"));
        XYChart.Series<String, Number> lateSeries = new XYChart.Series<>();
        lateSeries.setName(Lang.get("late"));
        for (int w = 1; w <= weeks; w++) {
            String label = String.format(Lang.get("week.short"), w);
            presentSeries.getData().add(new XYChart.Data<>(label, weekPresent[w]));
            absentSeries.getData().add(new XYChart.Data<>(label, weekAbsent[w]));
            lateSeries.getData().add(new XYChart.Data<>(label, weekLate[w]));
        }
        bar.getData().addAll(presentSeries, absentSeries, lateSeries);
        bar.setPrefWidth(720);
        bar.setAnimated(false);

        PieChart pie = new PieChart(FXCollections.observableArrayList(
                new PieChart.Data(Lang.get("present"), present),
                new PieChart.Data(Lang.get("absent"), absent),
                new PieChart.Data(Lang.get("late"), late)));
        pie.setTitle(Lang.get("stats.share"));
        pie.setAnimated(false);
        pie.setPrefWidth(340);

        HBox charts = new HBox(16, bar, pie);
        HBox.setHgrow(bar, Priority.ALWAYS);
        charts.setAlignment(Pos.CENTER);

        Button close = new Button(Lang.get("close"));
        close.setOnAction(e -> ((Stage) close.getScene().getWindow()).close());
        HBox buttons = new HBox(close);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(14, cards, charts, buttons);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("stats-root");

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(Lang.get("stats") + " · " + subjectName);
        javafx.scene.Scene scene = new javafx.scene.Scene(root, 1120, 720);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static VBox card(String caption, String value) {
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("stat-value");
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("stat-caption");
        VBox box = new VBox(2, valueLabel, captionLabel);
        box.getStyleClass().add("stat-card");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10, 18, 10, 18));
        return box;
    }
}