package com.jarnsen.atak.mrs.plugin;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

final class MrsDrawing {

    static final int DEFAULT_FILL = 0x2A21B6C7;

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
        d.label = o.optString("label", "");
        d.originLat = o.getDouble("originLat");
        d.originLon = o.getDouble("originLon");
        d.targetLat = o.getDouble("targetLat");
        d.targetLon = o.getDouble("targetLon");
        d.originSelf = o.optBoolean("originSelf", false);
        d.targetSelf = o.optBoolean("targetSelf", false);
        d.originMarkerUid = o.optString("originMarkerUid", "");
        d.targetMarkerUid = o.optString("targetMarkerUid", "");
        d.fillColor = o.optInt("fillColor", DEFAULT_FILL);
        d.fillAlpha = o.optInt("fillAlpha", 48);
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
}
