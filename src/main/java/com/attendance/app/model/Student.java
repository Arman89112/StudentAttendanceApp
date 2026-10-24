package com.attendance.app.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * One row of the attendance table: identity plus the status of every cell.
 * Statuses are keyed by week and lesson, both zero based. The rate and late
 * counters are observable so the summary columns update on their own.
 */
public class Student {
    private final SimpleStringProperty externalId = new SimpleStringProperty();
    private final SimpleStringProperty name = new SimpleStringProperty();
    private final ReadOnlyDoubleWrapper rate = new ReadOnlyDoubleWrapper(-1);
    private final ReadOnlyIntegerWrapper late = new ReadOnlyIntegerWrapper(0);
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final Map<Long, Integer> statuses = new HashMap<>();
    private final int databaseId;
    private int present;
    private int marked;

    public Student(int databaseId, String externalId, String name) {
        this.databaseId = databaseId;
        this.externalId.set(externalId);
        this.name.set(name);
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public String getExternalId() {
        return externalId.get();
    }

    public StringProperty idProperty() {
        return externalId;
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public static long key(int week, int lesson) {
        return ((long) week << 32) | (lesson & 0xFFFFFFFFL);
    }

    public int status(int week, int lesson) {
        return statuses.getOrDefault(key(week, lesson), 0);
    }

    public void setStatus(int week, int lesson, int status) {
        if (status == 0) {
            statuses.remove(key(week, lesson));
        } else {
            statuses.put(key(week, lesson), status);
        }
        recompute();
    }

    /** Reads a whole row out of the cached grid, row is zero based. */
    public void loadStatuses(int[][][] grid, int row) {
        statuses.clear();
        for (int w = 0; w < grid[row].length; w++) {
            for (int l = 0; l < grid[row][w].length; l++) {
                if (grid[row][w][l] != 0) {
                    statuses.put(key(w, l), grid[row][w][l]);
                }
            }
        }
        recompute();
    }

    private void recompute() {
        present = 0;
        late.set(0);
        marked = 0;
        for (int status : statuses.values()) {
            if (status == 3) {
                late.set(late.get() + 1);
            }
            if (status == 1 || status == 3) {
                present++;
            }
            if (status == 1 || status == 2 || status == 3) {
                marked++;
            }
        }
        rate.set(marked == 0 ? -1 : present * 100.0 / marked);
    }

    /** 0..100 or -1 when nothing has been marked yet. */
    public double getRate() {
        return rate.get();
    }

    public ReadOnlyDoubleProperty rateProperty() {
        return rate.getReadOnlyProperty();
    }

    public int getLate() {
        return late.get();
    }

    public ReadOnlyIntegerProperty lateProperty() {
        return late.getReadOnlyProperty();
    }

    public int getMarked() {
        return marked;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean value) {
        selected.set(value);
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }
}
