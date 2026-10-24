package com.attendance.app.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class Subject {
    private final SimpleIntegerProperty id;
    private final SimpleIntegerProperty semesterId;
    private final SimpleStringProperty name;
    private final SimpleIntegerProperty weekCount;
    private final SimpleIntegerProperty lessonCount;

    public Subject(int id, int semesterId, String name, int weekCount, int lessonCount) {
        this.id = new SimpleIntegerProperty(id);
        this.semesterId = new SimpleIntegerProperty(semesterId);
        this.name = new SimpleStringProperty(name);
        this.weekCount = new SimpleIntegerProperty(weekCount);
        this.lessonCount = new SimpleIntegerProperty(lessonCount);
    }

    public int getId() { return id.get(); }
    public int getSemesterId() { return semesterId.get(); }
    public String getName() { return name.get(); }
    public int getWeekCount() { return weekCount.get(); }
    public int getLessonCount() { return lessonCount.get(); }

    public SimpleIntegerProperty idProperty() { return id; }
    public SimpleIntegerProperty semesterIdProperty() { return semesterId; }
    public SimpleStringProperty nameProperty() { return name; }
    public SimpleIntegerProperty weekCountProperty() { return weekCount; }
    public SimpleIntegerProperty lessonCountProperty() { return lessonCount; }

    @Override
    public String toString() {
        return getName();
    }
}
