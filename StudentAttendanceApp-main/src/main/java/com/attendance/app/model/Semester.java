package com.attendance.app.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public class Semester {
    private final SimpleIntegerProperty id;
    private final SimpleStringProperty name;

    public Semester(int id, String name) {
        this.id = new SimpleIntegerProperty(id);
        this.name = new SimpleStringProperty(name);
    }

    public int getId() { return id.get(); }
    public String getName() { return name.get(); }

    public SimpleIntegerProperty idProperty() { return id; }
    public SimpleStringProperty nameProperty() { return name; }

    @Override
    public String toString() {
        return getName();
    }
}
