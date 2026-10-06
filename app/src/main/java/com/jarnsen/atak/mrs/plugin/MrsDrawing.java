package com.jarnsen.atak.mrs.plugin;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

final class MrsDrawing {

    static final int DEFAULT_FILL = 0x2A21B6C7;
    static final int MAX_LABEL_LENGTH = 120;

    final String id;
    String label;

    double originLat;
    double originLon;
    double targetLat;
    double targetLon;

    boolean originSelf;
    boolean targetSelf;
    String originMarkerUid = "";
    String targetMarkerUid = "";

    int fillColor = DEFAULT_FILL;
    int fillAlpha = 48;

    boolean showHalfKm = true;
    boolean showKm = true;
    boolean showRangeLabels = true;
    boolean showBracket = true;
    boolean showTargetMarker = true;
    boolean showFill = true;
    boolean visible = true;

    long updatedAt = System.currentTimeMillis();

    MrsDrawing() {
        this(UUID.randomUUID().toString());
    }

    MrsDrawing(String id) {
        this.id = id == null || id.isEmpty()
                ? UUID.randomUUID().toString()
                : id;
    }

    MrsDrawing copy() {
        MrsDrawing d = new MrsDrawing(id);
        d.label = label;
        d.originLat = originLat;
        d.originLon = originLon;
        d.targetLat = targetLat;
        d.targetLon = targetLon;
        d.originSelf = originSelf;
        d.targetSelf = targetSelf;
        d.originMarkerUid = originMarkerUid;
        d.targetMarkerUid = targetMarkerUid;
        d.fillColor = fillColor;
        d.fillAlpha = fillAlpha;
        d.showHalfKm = showHalfKm;
        d.showKm = showKm;
        d.showRangeLabels = showRangeLabels;
        d.showBracket = showBracket;
        d.showTargetMarker = showTargetMarker;
        d.showFill = showFill;
        d.visible = visible;
        d.updatedAt = updatedAt;
        return d;
    }

    MrsDrawing copyAsNew() {
        MrsDrawing d = new MrsDrawing();
        d.label = label;
        d.originLat = originLat;
        d.originLon = originLon;
        d.targetLat = targetLat;
        d.targetLon = targetLon;
        d.originSelf = originSelf;
        d.targetSelf = targetSelf;
        d.originMarkerUid = originMarkerUid;
        d.targetMarkerUid = targetMarkerUid;
        d.fillColor = fillColor;
        d.fillAlpha = fillAlpha;
        d.showHalfKm = showHalfKm;
        d.showKm = showKm;
        d.showRangeLabels = showRangeLabels;
        d.showBracket = showBracket;
        d.showTargetMarker = showTargetMarker;
        d.showFill = showFill;
        d.visible = visible;
        d.updatedAt = System.currentTimeMillis();
        return d;
    }

    GeoPointMetaData originPoint() {
        return GeoPointMetaData.wrap(new GeoPoint(originLat, originLon));
    }

    GeoPointMetaData targetPoint() {
        return GeoPointMetaData.wrap(new GeoPoint(targetLat, targetLon));
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("label", label == null ? "" : label);
        o.put("originLat", originLat);
        o.put("originLon", originLon);
        o.put("targetLat", targetLat);
        o.put("targetLon", targetLon);
        o.put("originSelf", originSelf);
        o.put("targetSelf", targetSelf);
        o.put("originMarkerUid", originMarkerUid == null ? "" : originMarkerUid);
        o.put("targetMarkerUid", targetMarkerUid == null ? "" : targetMarkerUid);
        o.put("fillColor", fillColor);
        o.put("fillAlpha", fillAlpha);
        o.put("showHalfKm", showHalfKm);
        o.put("showKm", showKm);
        o.put("showRangeLabels", showRangeLabels);
        o.put("showBracket", showBracket);
        o.put("showTargetMarker", showTargetMarker);
        o.put("showFill", showFill);
        o.put("visible", visible);
        o.put("updatedAt", updatedAt);
        return o;
    }

    static MrsDrawing fromJson(JSONObject o) throws JSONException {
        MrsDrawing d = new MrsDrawing(o.getString("id"));
        String label = o.optString("label", "");
        d.label = label.length() > MAX_LABEL_LENGTH
                ? label.substring(0, MAX_LABEL_LENGTH)
                : label;
        d.originLat = readCoordinate(o, "originLat", 90.0);
        d.originLon = readCoordinate(o, "originLon", 180.0);
        d.targetLat = readCoordinate(o, "targetLat", 90.0);
        d.targetLon = readCoordinate(o, "targetLon", 180.0);
        d.originSelf = o.optBoolean("originSelf", false);
        d.targetSelf = o.optBoolean("targetSelf", false);
        d.originMarkerUid = o.optString("originMarkerUid", "");
        d.targetMarkerUid = o.optString("targetMarkerUid", "");
        d.fillColor = o.optInt("fillColor", DEFAULT_FILL);
        d.fillAlpha = Math.max(0, Math.min(255, o.optInt("fillAlpha", 48)));
        d.showHalfKm = o.optBoolean("showHalfKm", true);
        d.showKm = o.optBoolean("showKm", true);
        d.showRangeLabels = o.optBoolean("showRangeLabels", true);
        d.showBracket = o.optBoolean("showBracket", true);
        d.showTargetMarker = o.optBoolean("showTargetMarker", true);
        d.showFill = o.optBoolean("showFill", true);
        d.visible = o.optBoolean("visible", true);
        d.updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
        return d;
    }

    /** Reads a finite coordinate and rejects values outside +/- limit. */
    private static double readCoordinate(
            JSONObject o,
            String key,
            double limit) throws JSONException {
        double value = o.getDouble(key);
        if (Double.isNaN(value)
                || Double.isInfinite(value)
                || Math.abs(value) > limit) {
            throw new JSONException("Ungültiger Wert für " + key);
        }
        return value;
    }
}
