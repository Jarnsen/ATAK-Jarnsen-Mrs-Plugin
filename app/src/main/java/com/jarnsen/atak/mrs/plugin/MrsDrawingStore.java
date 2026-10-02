package com.jarnsen.atak.mrs.plugin;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class MrsDrawingStore {

    private static final String PREF_DRAWINGS =
            "jarnsen.mrs.drawings.v3";
    private static final String PREF_DRAWINGS_BACKUP =
            "jarnsen.mrs.drawings.backup.v1";
    private static final String PREF_LAST_MGRS =
            "jarnsen.mrs.last_mgrs";
    private static final String PREF_UNDO =
            "jarnsen.mrs.undo.v1";
    private static final String PREF_REDO =
            "jarnsen.mrs.redo.v1";

    private final SharedPreferences prefs;

    MrsDrawingStore(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    List<MrsDrawing> load() {
        String primary = prefs.getString(PREF_DRAWINGS, "[]");
        if (isSnapshotValid(primary)) {
            return decodeDrawings(primary);
        }

        String backup = prefs.getString(PREF_DRAWINGS_BACKUP, "[]");
        if (isSnapshotValid(backup)) {
            prefs.edit().putString(PREF_DRAWINGS, backup).apply();
            return decodeDrawings(backup);
        }

        return new ArrayList<>();
    }

    void save(Collection<MrsDrawing> drawings) {
        String next = encodeDrawings(drawings);
        String current = prefs.getString(PREF_DRAWINGS, "[]");

        SharedPreferences.Editor editor = prefs.edit();
        if (!next.equals(current) && isSnapshotValid(current)) {
            editor.putString(PREF_DRAWINGS_BACKUP, current);
        }
        editor.putString(PREF_DRAWINGS, next).apply();
    }

    String snapshot(Collection<MrsDrawing> drawings) {
        return encodeDrawings(drawings);
    }

    List<MrsDrawing> restore(String snapshot) {
        return decodeDrawings(snapshot);
    }

    String getBackupSnapshot() {
        String backup = prefs.getString(PREF_DRAWINGS_BACKUP, "");
        return isSnapshotValid(backup) ? backup : "";
    }

    void saveHistory(
            Collection<String> undoSnapshots,
            Collection<String> redoSnapshots) {
        prefs.edit()
                .putString(PREF_UNDO, encodeHistory(undoSnapshots))
                .putString(PREF_REDO, encodeHistory(redoSnapshots))
                .apply();
    }

    List<String> loadUndoHistory() {
        return decodeHistory(prefs.getString(PREF_UNDO, "[]"));
    }

    List<String> loadRedoHistory() {
        return decodeHistory(prefs.getString(PREF_REDO, "[]"));
    }

    void saveLastMgrs(String mgrs) {
        if (mgrs == null || mgrs.trim().isEmpty()) {
            return;
        }
        prefs.edit().putString(PREF_LAST_MGRS, mgrs.trim()).apply();
    }

    String getLastMgrs() {
        return prefs.getString(PREF_LAST_MGRS, "");
    }

    String exportPackage(Collection<MrsDrawing> drawings) {
        try {
            JSONObject root = new JSONObject();
            root.put("format", "jarnsen-mrs");
            root.put("version", 1);
            root.put("exportedAt", System.currentTimeMillis());
            root.put("drawings", new JSONArray(encodeDrawings(drawings)));
            return root.toString(2);
        } catch (JSONException ignored) {
            return encodeDrawings(drawings);
        }
    }

    List<MrsDrawing> importPackage(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }

        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            return isSnapshotValid(trimmed)
                    ? decodeDrawings(trimmed)
                    : null;
        }

        try {
            JSONObject root = new JSONObject(trimmed);
            if (!"jarnsen-mrs".equals(root.optString("format", ""))) {
                return null;
            }
            JSONArray drawings = root.getJSONArray("drawings");
            String snapshot = drawings.toString();
            return isSnapshotValid(snapshot)
                    ? decodeDrawings(snapshot)
                    : null;
        } catch (JSONException ignored) {
            return null;
        }
    }

    static String encodeDrawings(Collection<MrsDrawing> drawings) {
        JSONArray a = new JSONArray();
        if (drawings != null) {
            for (MrsDrawing d : drawings) {
                try {
                    a.put(d.toJson());
                } catch (JSONException ignored) {
                }
            }
        }
        return a.toString();
    }

    static List<MrsDrawing> decodeDrawings(String json) {
        List<MrsDrawing> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < a.length(); i++) {
                out.add(MrsDrawing.fromJson(a.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
            out.clear();
        }
        return out;
    }

    static boolean isSnapshotValid(String json) {
        if (json == null) {
            return false;
        }
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                MrsDrawing.fromJson(a.getJSONObject(i));
            }
            return true;
        } catch (JSONException ignored) {
            return false;
        }
    }

    static String encodeHistory(Collection<String> snapshots) {
        JSONArray out = new JSONArray();
        if (snapshots != null) {
            for (String snapshot : snapshots) {
                if (snapshot != null && isSnapshotValid(snapshot)) {
                    out.put(snapshot);
                }
            }
        }
        return out.toString();
    }

    static List<String> decodeHistory(String json) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < a.length(); i++) {
                String snapshot = a.getString(i);
                if (isSnapshotValid(snapshot)) {
                    out.add(snapshot);
                }
            }
        } catch (JSONException ignored) {
            out.clear();
        }
        return out;
    }
}
