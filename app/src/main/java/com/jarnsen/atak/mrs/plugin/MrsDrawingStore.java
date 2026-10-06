package com.jarnsen.atak.mrs.plugin;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MrsDrawingStore {

    /** Maximum size of an import file; larger files are rejected. */
    static final int MAX_IMPORT_BYTES = 5 * 1000 * 1000;
    /** Maximum number of drawings accepted from one import file. */
    static final int MAX_IMPORT_DRAWINGS = 500;

    /**
     * Private preference file of this plugin. Earlier versions wrote into
     * ATAK's global default preferences; those keys are migrated once.
     */
    private static final String STORE_NAME = "jarnsen_mrs_store";
    private static final String PREF_MIGRATED = "jarnsen.mrs.migrated.v1";

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

    private static final String[] LEGACY_KEYS = {
            PREF_DRAWINGS,
            PREF_DRAWINGS_BACKUP,
            PREF_LAST_MGRS,
            PREF_UNDO,
            PREF_REDO,
            "jarnsen.mrs.next_number",
            "jarnsen.mrs.update.last_check"
    };

    private final SharedPreferences prefs;

    MrsDrawingStore(Context context) {
        prefs = context.getSharedPreferences(
                STORE_NAME,
                Context.MODE_PRIVATE
        );
        migrateLegacyPreferences(
                PreferenceManager.getDefaultSharedPreferences(context),
                prefs
        );
    }

    /** Plugin-private preferences (also used for small UI settings). */
    SharedPreferences preferences() {
        return prefs;
    }

    /**
     * Moves the keys written by versions up to 0.4.7 out of ATAK's global
     * preferences into the private file. The legacy keys are only removed
     * after the private file was written successfully.
     */
    private static void migrateLegacyPreferences(
            SharedPreferences legacy,
            SharedPreferences target) {
        if (target.getBoolean(PREF_MIGRATED, false)) {
            return;
        }

        Map<String, ?> all = legacy.getAll();
        SharedPreferences.Editor out = target.edit();
        SharedPreferences.Editor cleanup = legacy.edit();
        boolean removedAny = false;

        for (String key : LEGACY_KEYS) {
            if (!all.containsKey(key)) {
                continue;
            }
            Object value = all.get(key);
            if (value instanceof String) {
                out.putString(key, (String) value);
            } else if (value instanceof Integer) {
                out.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                out.putLong(key, (Long) value);
            } else if (value instanceof Boolean) {
                out.putBoolean(key, (Boolean) value);
            } else {
                continue;
            }
            cleanup.remove(key);
            removedAny = true;
        }

        out.putBoolean(PREF_MIGRATED, true);
        if (out.commit() && removedAny) {
            cleanup.apply();
        }
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
        return parseImport(json);
    }

    /**
     * Parses an import file. Returns null for anything that is not a valid
     * export (wrong format, too large, too many drawings, coordinates out of
     * range). Drawing ids are made unique inside the returned list.
     */
    static List<MrsDrawing> parseImport(String json) {
        if (json == null || json.length() > MAX_IMPORT_BYTES) {
            return null;
        }

        String trimmed = json.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String snapshot;
        try {
            if (trimmed.startsWith("[")) {
                snapshot = trimmed;
            } else {
                JSONObject root = new JSONObject(trimmed);
                if (!"jarnsen-mrs".equals(root.optString("format", ""))) {
                    return null;
                }
                snapshot = root.getJSONArray("drawings").toString();
            }
            if (new JSONArray(snapshot).length() > MAX_IMPORT_DRAWINGS) {
                return null;
            }
        } catch (JSONException ignored) {
            return null;
        }

        if (!isSnapshotValid(snapshot)) {
            return null;
        }
        return withUniqueIds(decodeDrawings(snapshot));
    }

    /** Gives every drawing in the list a distinct id. */
    static List<MrsDrawing> withUniqueIds(List<MrsDrawing> drawings) {
        Set<String> seen = new HashSet<>();
        List<MrsDrawing> out = new ArrayList<>(drawings.size());
        for (MrsDrawing drawing : drawings) {
            MrsDrawing value = drawing;
            if (!seen.add(value.id)) {
                value = drawing.copyAsNew();
                seen.add(value.id);
            }
            out.add(value);
        }
        return out;
    }

    /**
     * Drawings are expected to carry finite, in-range coordinates (see
     * MrsDrawing.fromJson and the isUsable check in the tool); a drawing
     * that cannot be serialized is skipped.
     */
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
