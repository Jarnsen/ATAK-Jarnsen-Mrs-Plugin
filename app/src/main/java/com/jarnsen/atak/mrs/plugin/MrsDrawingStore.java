package com.jarnsen.atak.mrs.plugin;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class MrsDrawingStore {

    private static final String PREF_DRAWINGS =
            "jarnsen.mrs.drawings.v3";
    private static final String PREF_LAST_MGRS =
            "jarnsen.mrs.last_mgrs";

    private final SharedPreferences prefs;

    MrsDrawingStore(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    List<MrsDrawing> load() {
        return decode(prefs.getString(PREF_DRAWINGS, "[]"));
    }

    void save(Collection<MrsDrawing> drawings) {
        prefs.edit().putString(PREF_DRAWINGS, encode(drawings)).apply();
    }

    String snapshot(Collection<MrsDrawing> drawings) {
        return encode(drawings);
    }

    List<MrsDrawing> restore(String snapshot) {
        return decode(snapshot);
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

    private static String encode(Collection<MrsDrawing> drawings) {
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

    private static List<MrsDrawing> decode(String json) {
        List<MrsDrawing> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(json == null ? "[]" : json);
            for (int i = 0; i < a.length(); i++) {
                try {
                    out.add(MrsDrawing.fromJson(a.getJSONObject(i)));
                } catch (JSONException ignored) {
                }
            }
        } catch (JSONException ignored) {
        }
        return out;
    }
}
