package com.jarnsen.atak.mrs.plugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class MrsHistory {

    private final int maximum;
    private final ArrayDeque<String> undo = new ArrayDeque<>();
    private final ArrayDeque<String> redo = new ArrayDeque<>();

    MrsHistory(int maximum) {
        this.maximum = Math.max(1, maximum);
    }

    boolean push(String snapshot) {
        if (snapshot == null) {
            return false;
        }
        if (!undo.isEmpty() && snapshot.equals(undo.peekLast())) {
            return false;
        }
        undo.addLast(snapshot);
        trim(undo);
        redo.clear();
        return true;
    }

    String undo(String currentSnapshot) {
        if (undo.isEmpty()) {
            return null;
        }
        if (currentSnapshot != null) {
            redo.addLast(currentSnapshot);
            trim(redo);
        }
        return undo.removeLast();
    }

    String redo(String currentSnapshot) {
        if (redo.isEmpty()) {
            return null;
        }
        if (currentSnapshot != null) {
            undo.addLast(currentSnapshot);
            trim(undo);
        }
        return redo.removeLast();
    }

    int undoSize() {
        return undo.size();
    }

    int redoSize() {
        return redo.size();
    }

    boolean discardLastUndoIfEquals(String snapshot) {
        if (snapshot == null
                || undo.isEmpty()
                || !snapshot.equals(undo.peekLast())) {
            return false;
        }
        undo.removeLast();
        return true;
    }

    List<String> undoSnapshots() {
        return new ArrayList<>(undo);
    }

    List<String> redoSnapshots() {
        return new ArrayList<>(redo);
    }

    void restore(
            Collection<String> undoSnapshots,
            Collection<String> redoSnapshots) {
        undo.clear();
        redo.clear();
        if (undoSnapshots != null) {
            for (String snapshot : undoSnapshots) {
                if (snapshot != null) {
                    undo.addLast(snapshot);
                    trim(undo);
                }
            }
        }
        if (redoSnapshots != null) {
            for (String snapshot : redoSnapshots) {
                if (snapshot != null) {
                    redo.addLast(snapshot);
                    trim(redo);
                }
            }
        }
    }

    private void trim(ArrayDeque<String> stack) {
        while (stack.size() > maximum) {
            stack.removeFirst();
        }
    }
}
