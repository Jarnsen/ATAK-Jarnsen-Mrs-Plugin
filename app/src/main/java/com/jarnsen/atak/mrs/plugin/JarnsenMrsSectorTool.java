package com.jarnsen.atak.mrs.plugin;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.Polyline;
import com.atakmap.android.maps.Shape;
import com.atakmap.android.toolbar.Tool;
import com.atakmap.android.toolbar.ToolManagerBroadcastReceiver;
import com.atakmap.android.toolbar.widgets.TextContainer;
import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.AtakMapView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Draws the Jarnsen Mrs range sector between two selectable points. Origin and
 * target can each be the ATAK self marker, an entered coordinate, or a point
 * selected on the map.
 *
 * Map geometry is generated from the true geodetic bearing. The displayed
 * direction is converted to Grid North and NATO 6400 mil.
 */
public class JarnsenMrsSectorTool extends Tool
        implements MapEventDispatcher.MapEventDispatchListener,
        MapEventDispatcher.OnMapEventListener,
        PointMapItem.OnPointChangedListener,
        AtakMapView.OnMapMovedListener {

    public static final String TOOL_IDENTIFIER =
            "com.jarnsen.atak.mrs.tool.SECTOR";

    private static final double MAX_RANGE_M = 8000.0;
    private static final double RANGE_STEP_M = 500.0;
    private static final String PREF_NEXT_MRS_NUMBER =
            "jarnsen.mrs.next_number";
    private static final String META_MRS_OVERLAY =
            "jarnsen.mrs.overlay";

    // 600 NATO mil = 33.75 degrees.
    private static final double HALF_SECTOR_MIL = 600.0;
    private static final double HALF_SECTOR_DEG =
            HALF_SECTOR_MIL * 360.0 / 6400.0;

    private static final int COLOR_PRIMARY = Color.rgb(102, 245, 255);
    private static final int COLOR_PRIMARY_SOFT =
            Color.argb(180, 102, 245, 255);
    private static final int COLOR_PRIMARY_FAINT =
            Color.argb(130, 102, 245, 255);
    private static final int COLOR_FILL =
            Color.argb(42, 33, 182, 199);
    private static final int COLOR_TARGET =
            Color.rgb(255, 68, 68);
    private static final String[] SECTOR_COLOR_NAMES = {
            "Weiß",
            "Rot",
            "Gelb",
            "Blau",
            "Grün",
            "Schwarz"
    };
    private static final int[] SECTOR_COLORS = {
            Color.WHITE,
            Color.rgb(220, 45, 45),
            Color.rgb(255, 214, 0),
            Color.rgb(40, 120, 255),
            Color.rgb(40, 180, 90),
            Color.BLACK
    };

    private final MapView mapView;
    private final MapGroup overlayGroup;
    private final TextContainer prompt;
    private final List<MapItem> overlayItems = new ArrayList<>();

    private boolean selectionActive;
    private boolean targetDragMoved;
    private boolean editingExistingPoint;
    private int sectorFillColor = COLOR_FILL;
    private double lastVisualResolution = Double.NaN;
    private String drawingLabel;
    private SelectionStage selectionStage = SelectionStage.NONE;
    private AlertDialog activeDialog;

    private final MapEventDispatcher.MapEventDispatchListener
            overlayTapListener = event -> {
                if (selectionActive
                        || activeDialog != null
                        || event == null
                        || !MapEvent.ITEM_CLICK.equals(event.getType())) {
                    return;
                }

                MapItem item = event.getItem();
                if (item == null
                        || !item.getMetaBoolean(META_MRS_OVERLAY, false)
                        || originPoint == null
                        || targetPoint == null) {
                    return;
                }

                mapView.post(() -> {
                    if (!selectionActive && activeDialog == null) {
                        ToolManagerBroadcastReceiver.getInstance().startTool(
                                TOOL_IDENTIFIER,
                                new Bundle()
                        );
                    }
                });
            };

    private Marker selfMarker;
    private PointMapItem originItem;
    private GeoPointMetaData originPoint;
    private PointMapItem targetItem;
    private GeoPointMetaData targetPoint;

    private enum SelectionStage {
        NONE,
        ORIGIN,
        TARGET
    }

    public JarnsenMrsSectorTool(MapView mapView, MapGroup overlayGroup) {
        super(mapView, TOOL_IDENTIFIER);
        this.mapView = mapView;
        this.overlayGroup = overlayGroup;
        this.prompt = TextContainer.getInstance();

        ToolManagerBroadcastReceiver.getInstance().registerTool(
                TOOL_IDENTIFIER,
                this
        );

        attachSelfListener();
        mapView.addOnMapMovedListener(this);
        mapView.getMapEventDispatcher().addMapEventListenerToBase(
                MapEvent.ITEM_CLICK,
                overlayTapListener
        );
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        attachSelfListener();
        if (originPoint != null && targetPoint != null) {
            showExistingDrawingDialog();
        } else {
            startNewSetup();
        }
        return true;
    }

    @Override
    public void onToolEnd() {
        stopMapSelection();

        if (activeDialog != null) {
            activeDialog.dismiss();
            activeDialog = null;
        }

        super.onToolEnd();
    }

    @Override
    public void onMapEvent(MapEvent event) {
        String eventType = event.getType();
        GeoPointMetaData clicked = findPoint(event);
        if (clicked == null || !isUsable(clicked.get())) {
            return;
        }

        if (selectionStage == SelectionStage.TARGET
                && isTargetDragEvent(eventType)) {
            boolean released = MapEvent.MAP_RELEASE.equals(eventType)
                    || MapEvent.ITEM_RELEASE.equals(eventType)
                    || MapEvent.ITEM_DRAG_DROPPED.equals(eventType);

            if (!isDifferentFromOrigin(clicked.get())) {
                if (released) {
                    showSamePointWarning();
                }
                return;
            }

            if (MapEvent.MAP_DRAW.equals(eventType)
                    || MapEvent.ITEM_DRAG_STARTED.equals(eventType)
                    || MapEvent.ITEM_DRAG_CONTINUED.equals(eventType)) {
                targetDragMoved = true;
            }

            MapItem boundItem = released && !targetDragMoved
                    ? event.getItem()
                    : null;
            setTarget(clicked, boundItem);

            if (released) {
                stopMapSelection();
                if (editingExistingPoint) {
                    finishPointEdit();
                } else {
                    finishSetup();
                }
            }
            return;
        }

        if (!MapEvent.ITEM_CLICK.equals(eventType)
                && !MapEvent.MAP_CLICK.equals(eventType)) {
            return;
        }

        if (selectionStage == SelectionStage.ORIGIN) {
            setOrigin(clicked, event.getItem());
            stopMapSelection();
            if (editingExistingPoint) {
                finishPointEdit();
            } else {
                showPointSourceDialog(SelectionStage.TARGET);
            }
        } else if (selectionStage == SelectionStage.TARGET) {
            if (!isDifferentFromOrigin(clicked.get())) {
                showSamePointWarning();
                return;
            }

            setTarget(clicked, event.getItem());
            stopMapSelection();
            if (editingExistingPoint) {
                finishPointEdit();
            } else {
                finishSetup();
            }
        }
    }

    @Override
    public void onMapItemMapEvent(MapItem item, MapEvent event) {
        if (!MapEvent.ITEM_CLICK.equals(event.getType())
                || selectionActive
                || activeDialog != null
                || !overlayItems.contains(item)) {
            return;
        }

        ToolManagerBroadcastReceiver.getInstance().startTool(
                TOOL_IDENTIFIER,
                new Bundle()
        );
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        if (item == null) {
            return;
        }

        if (item == originItem) {
            originPoint = item.getGeoPointMetaData();
        }

        if (item == targetItem) {
            targetPoint = item.getGeoPointMetaData();
        }

        redraw();
    }

    @Override
    public void onMapMoved(AtakMapView view, boolean animate) {
        if (originPoint == null || targetPoint == null) {
            return;
        }

        double resolution = mapView.getMapResolution();
        if (!Double.isFinite(resolution) || resolution <= 0.0) {
            return;
        }

        if (Double.isNaN(lastVisualResolution)
                || Math.abs(resolution - lastVisualResolution)
                / lastVisualResolution >= 0.08) {
            lastVisualResolution = resolution;
            mapView.post(this::redraw);
        }
    }

    @Override
    public void dispose() {
        if (selectionActive || activeDialog != null) {
            requestEndTool();
        }

        detachEndpointListeners();

        if (selfMarker != null) {
            selfMarker.removeOnPointChangedListener(this);
            selfMarker = null;
        }

        clearOverlayItems();
        mapView.removeOnMapMovedListener(this);
        mapView.getMapEventDispatcher().removeMapEventListenerFromBase(
                MapEvent.ITEM_CLICK,
                overlayTapListener
        );

        ToolManagerBroadcastReceiver.getInstance().unregisterTool(
                TOOL_IDENTIFIER
        );
    }

    private void attachSelfListener() {
        Marker current = mapView.getSelfMarker();
        if (current == selfMarker) {
            return;
        }

        boolean originWasSelf = originItem != null && originItem == selfMarker;
        boolean targetWasSelf = targetItem != null && targetItem == selfMarker;

        if (selfMarker != null) {
            selfMarker.removeOnPointChangedListener(this);
        }

        selfMarker = current;
        if (selfMarker != null) {
            selfMarker.addOnPointChangedListener(this);

            if (originWasSelf) {
                originItem = selfMarker;
                originPoint = selfMarker.getGeoPointMetaData();
            }
            if (targetWasSelf) {
                targetItem = selfMarker;
                targetPoint = selfMarker.getGeoPointMetaData();
            }
        }
    }

    private void showPointSourceDialog(final SelectionStage stage) {
        final String pointName = stage == SelectionStage.ORIGIN
                ? "Startpunkt"
                : "Zielpunkt";

        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle(pointName + " wählen")
                .setItems(
                        new String[]{
                                "Eigene Position",
                                "MGRS eingeben",
                                "Breite/Länge eingeben",
                                "Auf der Karte wählen"
                        },
                        (ignored, which) -> {
                            activeDialog = null;
                            if (which == 0) {
                                chooseSelfPosition(stage);
                            } else if (which == 1) {
                                showMgrsCoordinateDialog(stage);
                            } else if (which == 2) {
                                showCoordinateDialog(stage);
                            } else {
                                beginMapSelection(stage);
                            }
                        }
                )
                .setOnCancelListener(ignored -> {
                    activeDialog = null;
                    cancelPointSelection();
                })
                .create();

        activeDialog = dialog;
        dialog.show();
    }

    private void showExistingDrawingDialog() {
        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle(getDrawingLabel())
                .setMessage(
                        "Zielkoordinate (MGRS)\n"
                                + formatTargetCoordinate()
                )
                .setItems(
                        new String[]{
                                "Bearbeiten",
                                "Entfernen",
                                "Schließen"
                        },
                        (ignored, which) -> {
                            activeDialog = null;
                            if (which == 0) {
                                showEditDrawingDialog();
                            } else if (which == 1) {
                                resetEndpoints();
                                drawingLabel = null;
                                closeTool();
                            } else {
                                closeTool();
                            }
                        }
                )
                .setOnCancelListener(ignored -> {
                    activeDialog = null;
                    closeTool();
                })
                .create();

        activeDialog = dialog;
        dialog.show();
    }

    private void showEditDrawingDialog() {
        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle(getDrawingLabel() + " bearbeiten")
                .setItems(
                        new String[]{
                                "Startpunkt ändern",
                                "Zielpunkt ändern",
                                "Start per MGRS eingeben",
                                "Ziel per MGRS eingeben",
                                "Beschriftung ändern",
                                "Farbe ändern",
                                "Zurück",
                                "Schließen"
                        },
                        (ignored, which) -> {
                            activeDialog = null;
                            if (which == 0) {
                                beginEditPoint(SelectionStage.ORIGIN);
                            } else if (which == 1) {
                                beginEditPoint(SelectionStage.TARGET);
                            } else if (which == 2) {
                                editingExistingPoint = true;
                                showMgrsCoordinateDialog(
                                        SelectionStage.ORIGIN);
                            } else if (which == 3) {
                                editingExistingPoint = true;
                                showMgrsCoordinateDialog(
                                        SelectionStage.TARGET);
                            } else if (which == 4) {
                                showLabelDialog(false);
                            } else if (which == 5) {
                                showColorSelectionDialog(false);
                            } else if (which == 6) {
                                showExistingDrawingDialog();
                            } else {
                                closeTool();
                            }
                        }
                )
                .setOnCancelListener(ignored -> {
                    activeDialog = null;
                    showExistingDrawingDialog();
                })
                .create();

        activeDialog = dialog;
        dialog.show();
    }

    private void startNewSetup() {
        editingExistingPoint = false;
        resetEndpoints();
        drawingLabel = null;
        showPointSourceDialog(SelectionStage.ORIGIN);
    }

    private void beginEditPoint(SelectionStage stage) {
        editingExistingPoint = true;
        showPointSourceDialog(stage);
    }

    private void chooseSelfPosition(SelectionStage stage) {
        attachSelfListener();
        if (selfMarker == null || !isUsable(selfMarker.getPoint())) {
            Toast.makeText(
                    mapView.getContext(),
                    "Eigene Position ist noch nicht verfügbar.",
                    Toast.LENGTH_SHORT
            ).show();
            showPointSourceDialog(stage);
            return;
        }

        if (stage == SelectionStage.TARGET
                && !isDifferentFromOrigin(selfMarker.getPoint())) {
            showSamePointWarning();
            showPointSourceDialog(stage);
            return;
        }

        if (stage == SelectionStage.ORIGIN) {
            setOrigin(selfMarker.getGeoPointMetaData(), selfMarker);
            if (editingExistingPoint) {
                finishPointEdit();
            } else {
                showPointSourceDialog(SelectionStage.TARGET);
            }
        } else {
            setTarget(selfMarker.getGeoPointMetaData(), selfMarker);
            if (editingExistingPoint) {
                finishPointEdit();
            } else {
                finishSetup();
            }
        }
    }

    private void showMgrsCoordinateDialog(final SelectionStage stage) {
        EditText mgrs = new EditText(mapView.getContext());
        mgrs.setHint("z. B. 32U MV 12345 67890");
        mgrs.setSingleLine(true);
        mgrs.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        );

        GeoPointMetaData current = stage == SelectionStage.ORIGIN
                ? originPoint
                : targetPoint;
        if (current != null && isUsable(current.get())) {
            mgrs.setText(CoordinateFormatUtilities.formatToString(
                    current.get(),
                    CoordinateFormat.MGRS
            ));
            mgrs.setSelection(mgrs.getText().length());
        }

        int padding = Math.round(
                20.0f * mapView.getResources().getDisplayMetrics().density
        );
        LinearLayout holder = new LinearLayout(mapView.getContext());
        holder.setPadding(padding, 0, padding, 0);
        holder.addView(mgrs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle((stage == SelectionStage.ORIGIN
                        ? "Startpunkt"
                        : "Zielpunkt") + " – MGRS")
                .setView(holder)
                .setPositiveButton("Übernehmen", null)
                .setNegativeButton("Zurück", (ignored, which) -> {
                    activeDialog = null;
                    showPointSourceDialog(stage);
                })
                .setOnCancelListener(ignored -> {
                    activeDialog = null;
                    cancelPointSelection();
                })
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
        ).setOnClickListener(button -> {
            GeoPoint entered = parseMgrsCoordinate(mgrs);
            if (entered == null) {
                return;
            }

            if (stage == SelectionStage.TARGET
                    && !isDifferentFromOrigin(entered)) {
                showSamePointWarning();
                return;
            }

            dialog.dismiss();
            activeDialog = null;
            GeoPointMetaData point = GeoPointMetaData.wrap(entered);
            if (stage == SelectionStage.ORIGIN) {
                setOrigin(point, null);
                if (editingExistingPoint) {
                    finishPointEdit();
                } else {
                    showPointSourceDialog(SelectionStage.TARGET);
                }
            } else {
                setTarget(point, null);
                if (editingExistingPoint) {
                    finishPointEdit();
                } else {
                    finishSetup();
                }
            }
        }));

        activeDialog = dialog;
        dialog.show();
    }

    private GeoPoint parseMgrsCoordinate(EditText mgrs) {
        String value = mgrs.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(
                    mapView.getContext(),
                    "Bitte eine MGRS-Koordinate eingeben.",
                    Toast.LENGTH_SHORT
            ).show();
            return null;
        }

        try {
            GeoPoint point = CoordinateFormatUtilities.convert(
                    value,
                    CoordinateFormat.MGRS
            );
            if (!isUsable(point)) {
                throw new IllegalArgumentException();
            }
            return point;
        } catch (IllegalArgumentException ignored) {
            Toast.makeText(
                    mapView.getContext(),
                    "MGRS ungültig. Beispiel: 32U MV 12345 67890",
                    Toast.LENGTH_SHORT
            ).show();
            return null;
        }
    }

    private void showCoordinateDialog(final SelectionStage stage) {
        LinearLayout fields = new LinearLayout(mapView.getContext());
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(
                20.0f * mapView.getResources().getDisplayMetrics().density
        );
        fields.setPadding(padding, 0, padding, 0);

        EditText latitude = makeCoordinateField(
                "Breitengrad, z. B. 52.5200"
        );
        EditText longitude = makeCoordinateField(
                "Längengrad, z. B. 13.4050"
        );
        fields.addView(latitude, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        fields.addView(longitude, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle((stage == SelectionStage.ORIGIN
                        ? "Startpunkt"
                        : "Zielpunkt") + " – Koordinaten")
                .setView(fields)
                .setPositiveButton("Übernehmen", null)
                .setNegativeButton("Zurück", (ignored, which) -> {
                    activeDialog = null;
                    showPointSourceDialog(stage);
                })
                .setOnCancelListener(ignored -> {
                    activeDialog = null;
                    cancelPointSelection();
                })
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
        ).setOnClickListener(button -> {
            GeoPoint entered = parseCoordinate(latitude, longitude);
            if (entered == null) {
                return;
            }

            if (stage == SelectionStage.TARGET
                    && !isDifferentFromOrigin(entered)) {
                showSamePointWarning();
                return;
            }

            dialog.dismiss();
            activeDialog = null;
            GeoPointMetaData point = GeoPointMetaData.wrap(entered);
            if (stage == SelectionStage.ORIGIN) {
                setOrigin(point, null);
                if (editingExistingPoint) {
                    finishPointEdit();
                } else {
                    showPointSourceDialog(SelectionStage.TARGET);
                }
            } else {
                setTarget(point, null);
                if (editingExistingPoint) {
                    finishPointEdit();
                } else {
                    finishSetup();
                }
            }
        }));

        activeDialog = dialog;
        dialog.show();
    }

    private EditText makeCoordinateField(String hint) {
        EditText field = new EditText(mapView.getContext());
        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED
        );
        return field;
    }

    private GeoPoint parseCoordinate(EditText latitude, EditText longitude) {
        try {
            double lat = Double.parseDouble(
                    latitude.getText().toString().trim().replace(',', '.')
            );
            double lon = Double.parseDouble(
                    longitude.getText().toString().trim().replace(',', '.')
            );

            if (lat < -90.0 || lat > 90.0
                    || lon < -180.0 || lon > 180.0) {
                throw new NumberFormatException();
            }
            return new GeoPoint(lat, lon);
        } catch (NumberFormatException ignored) {
            Toast.makeText(
                    mapView.getContext(),
                    "Bitte gültige Breiten- und Längengrade eingeben.",
                    Toast.LENGTH_SHORT
            ).show();
            return null;
        }
    }

    private void beginMapSelection(SelectionStage stage) {
        stopMapSelection();
        selectionStage = stage;

        MapEventDispatcher dispatcher = mapView.getMapEventDispatcher();
        dispatcher.pushListeners();
        dispatcher.clearListeners(MapEvent.ITEM_CLICK);
        dispatcher.clearListeners(MapEvent.MAP_CLICK);
        dispatcher.addMapEventListener(MapEvent.ITEM_CLICK, this);
        dispatcher.addMapEventListener(MapEvent.MAP_CLICK, this);

        if (stage == SelectionStage.TARGET) {
            dispatcher.clearListeners(MapEvent.ITEM_PRESS);
            dispatcher.clearListeners(MapEvent.ITEM_RELEASE);
            dispatcher.clearListeners(MapEvent.ITEM_DRAG_STARTED);
            dispatcher.clearListeners(MapEvent.ITEM_DRAG_CONTINUED);
            dispatcher.clearListeners(MapEvent.ITEM_DRAG_DROPPED);
            dispatcher.clearListeners(MapEvent.MAP_PRESS);
            dispatcher.clearListeners(MapEvent.MAP_DRAW);
            dispatcher.clearListeners(MapEvent.MAP_RELEASE);
            dispatcher.addMapEventListener(MapEvent.ITEM_PRESS, this);
            dispatcher.addMapEventListener(MapEvent.ITEM_RELEASE, this);
            dispatcher.addMapEventListener(MapEvent.ITEM_DRAG_STARTED, this);
            dispatcher.addMapEventListener(MapEvent.ITEM_DRAG_CONTINUED, this);
            dispatcher.addMapEventListener(MapEvent.ITEM_DRAG_DROPPED, this);
            dispatcher.addMapEventListener(MapEvent.MAP_PRESS, this);
            dispatcher.addMapEventListener(MapEvent.MAP_DRAW, this);
            dispatcher.addMapEventListener(MapEvent.MAP_RELEASE, this);
        }

        mapView.getMapTouchController().skipDeconfliction(true);
        prompt.displayPrompt(
                "Jarnsen Mrs: "
                        + (stage == SelectionStage.ORIGIN
                        ? "Startpunkt"
                        : "Zielpunkt")
                        + (stage == SelectionStage.TARGET
                        ? " berühren, ziehen und loslassen"
                        : " auf der Karte wählen")
        );
        targetDragMoved = false;
        selectionActive = true;
    }

    private void stopMapSelection() {
        if (!selectionActive) {
            selectionStage = SelectionStage.NONE;
            return;
        }

        prompt.closePrompt();
        mapView.getMapEventDispatcher().popListeners();
        mapView.getMapTouchController().skipDeconfliction(false);
        selectionActive = false;
        targetDragMoved = false;
        selectionStage = SelectionStage.NONE;
    }

    private static boolean isTargetDragEvent(String eventType) {
        return MapEvent.ITEM_PRESS.equals(eventType)
                || MapEvent.ITEM_RELEASE.equals(eventType)
                || MapEvent.ITEM_DRAG_STARTED.equals(eventType)
                || MapEvent.ITEM_DRAG_CONTINUED.equals(eventType)
                || MapEvent.ITEM_DRAG_DROPPED.equals(eventType)
                || MapEvent.MAP_PRESS.equals(eventType)
                || MapEvent.MAP_DRAW.equals(eventType)
                || MapEvent.MAP_RELEASE.equals(eventType);
    }

    private void finishSetup() {
        ensureDrawingLabel();
        redraw();
        showLabelDialog(true);
    }

    private void finishPointEdit() {
        editingExistingPoint = false;
        redraw();
        showExistingDrawingDialog();
    }

    private void cancelPointSelection() {
        stopMapSelection();
        if (editingExistingPoint) {
            editingExistingPoint = false;
            showExistingDrawingDialog();
        } else {
            closeTool();
        }
    }

    private void closeTool() {
        stopMapSelection();
        editingExistingPoint = false;
        ToolManagerBroadcastReceiver.getInstance().endCurrentTool();
    }

    private void showLabelDialog(boolean continueToColor) {
        ensureDrawingLabel();

        EditText label = new EditText(mapView.getContext());
        label.setHint("Optional, z. B. Mrs 1");
        label.setSingleLine(true);
        label.setText(drawingLabel);
        label.setSelection(label.getText().length());

        int padding = Math.round(
                20.0f * mapView.getResources().getDisplayMetrics().density
        );
        LinearLayout holder = new LinearLayout(mapView.getContext());
        holder.setPadding(padding, 0, padding, 0);
        holder.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle("Beschriftung / Beschreibung")
                .setMessage(
                        "Optional. Ohne eigene Eingabe wird automatisch "
                                + drawingLabel + " verwendet."
                )
                .setView(holder)
                .setPositiveButton("Übernehmen", (ignored, which) -> {
                    activeDialog = null;
                    String value = label.getText().toString().trim();
                    if (!value.isEmpty()) {
                        drawingLabel = value;
                    }
                    redraw();
                    if (continueToColor) {
                        showColorSelectionDialog(true);
                    } else {
                        showExistingDrawingDialog();
                    }
                })
                .setNegativeButton("Standard", (ignored, which) -> {
                    activeDialog = null;
                    redraw();
                    if (continueToColor) {
                        showColorSelectionDialog(true);
                    } else {
                        showExistingDrawingDialog();
                    }
                })
                .setOnCancelListener(ignored -> {
                    activeDialog = null;
                    redraw();
                    if (continueToColor) {
                        showColorSelectionDialog(true);
                    } else {
                        showExistingDrawingDialog();
                    }
                })
                .create();

        activeDialog = dialog;
        dialog.show();
    }

    private void ensureDrawingLabel() {
        if (drawingLabel != null && !drawingLabel.trim().isEmpty()) {
            return;
        }

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(mapView.getContext());
        int number = Math.max(1, prefs.getInt(PREF_NEXT_MRS_NUMBER, 1));
        drawingLabel = "Mrs " + number;
        prefs.edit().putInt(PREF_NEXT_MRS_NUMBER, number + 1).apply();
    }

    private String getDrawingLabel() {
        return drawingLabel == null || drawingLabel.trim().isEmpty()
                ? "Jarnsen Mrs"
                : drawingLabel.trim();
    }

    private void showColorSelectionDialog(boolean closeWhenDone) {
        CharSequence[] options = new CharSequence[SECTOR_COLOR_NAMES.length];
        for (int i = 0; i < SECTOR_COLOR_NAMES.length; i++) {
            SpannableString option = new SpannableString(
                    "■  " + SECTOR_COLOR_NAMES[i]
            );
            option.setSpan(
                    new ForegroundColorSpan(SECTOR_COLORS[i]),
                    0,
                    1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            options[i] = option;
        }

        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle("Sektorfarbe")
                .setItems(options, (ignored, which) -> {
                    activeDialog = null;
                    applySectorColor(SECTOR_COLORS[which]);
                    if (closeWhenDone) {
                        closeTool();
                    } else {
                        showExistingDrawingDialog();
                    }
                })
                .setNeutralButton("Standard", (ignored, which) -> {
                    activeDialog = null;
                    sectorFillColor = COLOR_FILL;
                    redraw();
                    if (closeWhenDone) {
                        closeTool();
                    } else {
                        showExistingDrawingDialog();
                    }
                })
                .setOnCancelListener(ignored -> {
                    activeDialog = null;
                    if (closeWhenDone) {
                        closeTool();
                    } else {
                        showExistingDrawingDialog();
                    }
                })
                .create();

        activeDialog = dialog;
        dialog.show();
    }

    private void applySectorColor(int color) {
        sectorFillColor = Color.argb(
                48,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
        redraw();
    }

    private void showSamePointWarning() {
        Toast.makeText(
                mapView.getContext(),
                "Start- und Zielpunkt müssen verschieden sein.",
                Toast.LENGTH_SHORT
        ).show();
    }

    private boolean isDifferentFromOrigin(GeoPoint candidate) {
        if (originPoint == null || !isUsable(originPoint.get())) {
            return true;
        }
        double distance = originPoint.get().distanceTo(candidate);
        return Double.isNaN(distance) || distance >= 1.0;
    }

    private void resetEndpoints() {
        detachEndpointListeners();
        originPoint = null;
        targetPoint = null;
        editingExistingPoint = false;
        lastVisualResolution = Double.NaN;
        clearOverlayItems();
    }

    private void setOrigin(GeoPointMetaData point, MapItem item) {
        detachOriginListener();
        originPoint = point;

        if (item instanceof PointMapItem) {
            originItem = (PointMapItem) item;
            originPoint = originItem.getGeoPointMetaData();
            if (originItem != selfMarker) {
                originItem.addOnPointChangedListener(this);
            }
        }
    }

    private void setTarget(GeoPointMetaData point, MapItem item) {
        detachTargetListener();

        targetPoint = point;

        if (item instanceof PointMapItem) {
            targetItem = (PointMapItem) item;
            targetPoint = targetItem.getGeoPointMetaData();
            if (targetItem != selfMarker) {
                targetItem.addOnPointChangedListener(this);
            }
        }

        redraw();
    }

    private void detachTargetListener() {
        if (targetItem != null) {
            if (targetItem != selfMarker) {
                targetItem.removeOnPointChangedListener(this);
            }
            targetItem = null;
        }
    }

    private void detachOriginListener() {
        if (originItem != null) {
            if (originItem != selfMarker) {
                originItem.removeOnPointChangedListener(this);
            }
            originItem = null;
        }
    }

    private void detachEndpointListeners() {
        detachOriginListener();
        detachTargetListener();
    }

    private void redraw() {
        clearOverlayItems();
        attachSelfListener();

        if (originPoint == null || targetPoint == null) {
            return;
        }

        GeoPoint own = originPoint.get();
        GeoPoint target = targetPoint.get();

        if (!isUsable(own) || !isUsable(target)) {
            return;
        }

        double targetDistance = own.distanceTo(target);
        if (Double.isNaN(targetDistance) || targetDistance < 1.0) {
            return;
        }

        double trueBearing = normalizeDegrees(own.bearingTo(target));
        double gridBearing = toGridBearing(own, target, trueBearing);
        int gridMil = degreesToMil(gridBearing);
        double bracketAnchor = targetDistance / 2.0;
        double visualResolution = getVisualResolution();
        lastVisualResolution = visualResolution;
        double bracketLabelOffset = clamp(
                visualResolution * 24.0,
                35.0,
                260.0
        );

        addSectorFill(own, trueBearing);
        addSectorBoundary(own, trueBearing - HALF_SECTOR_DEG);
        addSectorBoundary(own, trueBearing + HALF_SECTOR_DEG);

        for (double range = RANGE_STEP_M;
             range <= MAX_RANGE_M + 0.1;
             range += RANGE_STEP_M) {
            boolean fullKm = (((int) Math.round(range)) % 1000) == 0;
            addRangeArc(own, trueBearing, range, fullKm);
            addRangeTick(own, trueBearing, range, fullKm);
            addRangeLabel(own, trueBearing, range);
        }

        addCenterLine(own, target);
        addInteractionHitBox(own, target);
        addArrowHead(target, trueBearing);
        addTargetMarker(target, trueBearing);

        addCenterBracket(own, trueBearing, bracketAnchor);
        addBracketLabel(
                own,
                trueBearing,
                bracketAnchor,
                bracketLabelOffset,
                String.format(
                        Locale.GERMANY,
                        "%s  GR %04d mils",
                        getDrawingLabel(),
                        gridMil
                )
        );
        addBracketLabel(
                own,
                trueBearing,
                bracketAnchor,
                -bracketLabelOffset,
                formatTargetDistance(targetDistance)
        );
    }

    private void addSectorFill(GeoPoint own, double bearing) {
        List<GeoPoint> pts = new ArrayList<>();
        pts.add(own);

        final int steps = 56;
        double start = bearing - HALF_SECTOR_DEG;
        double span = HALF_SECTOR_DEG * 2.0;

        for (int i = 0; i <= steps; i++) {
            double b = start + span * i / steps;
            pts.add(GeoCalculations.pointAtDistance(own, b, MAX_RANGE_M));
        }

        pts.add(own);

        Polyline sector = makePolyline(
                pts,
                COLOR_PRIMARY_FAINT,
                1.0,
                Shape.BASIC_LINE_STYLE_SOLID
        );
        sector.setStyle(
                Polyline.STYLE_CLOSED_MASK
                        | Shape.STYLE_STROKE_MASK
                        | Shape.STYLE_FILLED_MASK
        );
        sector.setFillColor(sectorFillColor);
        addLocalItem(sector);
    }

    private void addSectorBoundary(GeoPoint own, double bearing) {
        List<GeoPoint> pts = new ArrayList<>();
        pts.add(own);
        pts.add(GeoCalculations.pointAtDistance(own, bearing, MAX_RANGE_M));

        addLocalItem(makePolyline(
                pts,
                Color.WHITE,
                2.3,
                Shape.BASIC_LINE_STYLE_SOLID
        ));
    }

    private void addRangeArc(
            GeoPoint own,
            double bearing,
            double range,
            boolean fullKm) {

        List<GeoPoint> pts = new ArrayList<>();

        final int steps = 42;
        double start = bearing - HALF_SECTOR_DEG;
        double span = HALF_SECTOR_DEG * 2.0;

        for (int i = 0; i <= steps; i++) {
            double b = start + span * i / steps;
            pts.add(GeoCalculations.pointAtDistance(own, b, range));
        }

        Polyline arc = makePolyline(
                pts,
                fullKm ? COLOR_PRIMARY : COLOR_PRIMARY_SOFT,
                fullKm ? 2.3 : 1.15,
                fullKm
                        ? Shape.BASIC_LINE_STYLE_SOLID
                        : Shape.BASIC_LINE_STYLE_DASHED
        );

        addLocalItem(arc);
    }

    private void addRangeTick(
            GeoPoint own,
            double bearing,
            double range,
            boolean fullKm) {

        double halfWidth = fullKm ? 55.0 : 38.0;

        List<GeoPoint> pts = new ArrayList<>();
        pts.add(pointFromAxis(own, bearing, range, halfWidth));
        pts.add(pointFromAxis(own, bearing, range, -halfWidth));

        addLocalItem(makePolyline(
                pts,
                Color.WHITE,
                fullKm ? 2.0 : 1.2,
                Shape.BASIC_LINE_STYLE_SOLID
        ));
    }

    /**
     * The distance text is carried by a short line segment immediately before
     * the corresponding arc, so the label stays aligned with the centerline
     * and appears on the start-point side of the range crossing.
     */
    private void addRangeLabel(
            GeoPoint own,
            double bearing,
            double range) {

        double start = Math.max(40.0, range - 245.0);
        double end = Math.max(80.0, range - 45.0);

        List<GeoPoint> pts = new ArrayList<>();
        pts.add(GeoCalculations.pointAtDistance(own, bearing, start));
        pts.add(GeoCalculations.pointAtDistance(own, bearing, end));

        Polyline labelLine = makePolyline(
                pts,
                Color.argb(1, 255, 255, 255),
                0.1,
                Shape.BASIC_LINE_STYLE_SOLID
        );

        labelLine.toggleMetaData("labels_on", true);
        labelLine.setLineLabel(formatRange(range));
        labelLine.setLabelTextSize(14);

        addLocalItem(labelLine);
    }

    private void addCenterLine(GeoPoint own, GeoPoint target) {
        List<GeoPoint> pts = new ArrayList<>();
        pts.add(own);
        pts.add(target);

        addLocalItem(makePolyline(
                pts,
                Color.WHITE,
                2.0,
                Shape.BASIC_LINE_STYLE_SOLID
        ));
    }

    private void addInteractionHitBox(GeoPoint own, GeoPoint target) {
        List<GeoPoint> pts = new ArrayList<>();
        pts.add(own);
        pts.add(target);

        Polyline hitBox = makePolyline(
                pts,
                Color.argb(2, 255, 255, 255),
                14.0,
                Shape.BASIC_LINE_STYLE_SOLID
        );
        hitBox.setMetaBoolean(META_MRS_OVERLAY, true);
        addLocalItem(hitBox);
    }

    private double getArrowLegMeters() {
        return clamp(
                getVisualResolution() * 32.0,
                45.0,
                360.0
        );
    }

    private void addArrowHead(GeoPoint target, double bearing) {
        double leg = getArrowLegMeters();
        double back = normalizeDegrees(bearing + 180.0);

        List<GeoPoint> left = new ArrayList<>();
        left.add(target);
        left.add(GeoCalculations.pointAtDistance(
                target,
                back - 24.0,
                leg
        ));

        List<GeoPoint> right = new ArrayList<>();
        right.add(target);
        right.add(GeoCalculations.pointAtDistance(
                target,
                back + 24.0,
                leg
        ));

        addLocalItem(makePolyline(
                left,
                Color.WHITE,
                2.0,
                Shape.BASIC_LINE_STYLE_SOLID
        ));
        addLocalItem(makePolyline(
                right,
                Color.WHITE,
                2.0,
                Shape.BASIC_LINE_STYLE_SOLID
        ));
    }

    private void addTargetMarker(GeoPoint target, double bearing) {
        double d = clamp(
                getVisualResolution() * 12.0,
                18.0,
                140.0
        );

        List<GeoPoint> cross1 = new ArrayList<>();
        cross1.add(pointFromAxis(target, bearing, -d, 0));
        cross1.add(pointFromAxis(target, bearing, d, 0));

        List<GeoPoint> cross2 = new ArrayList<>();
        cross2.add(pointFromAxis(target, bearing, 0, d));
        cross2.add(pointFromAxis(target, bearing, 0, -d));

        addLocalItem(makePolyline(
                cross1,
                COLOR_TARGET,
                2.7,
                Shape.BASIC_LINE_STYLE_SOLID
        ));
        addLocalItem(makePolyline(
                cross2,
                COLOR_TARGET,
                2.7,
                Shape.BASIC_LINE_STYLE_SOLID
        ));
    }

    /**
     * Exactly one central bracket pair, rotated 90 degrees compared with a
     * normal horizontal ")( ". The upper and lower curves open toward the
     * centerline.
     */
    private void addCenterBracket(
            GeoPoint own,
            double bearing,
            double anchor) {
        // Tie the bracket directly to the visible arrow size. The arrow size
        // itself follows ATAK's current meters-per-pixel resolution, so both
        // elements grow/shrink together while zooming.
        double arrowLeg = getArrowLegMeters();
        double maxHalfWidth = Math.max(
                20.0,
                Math.min(650.0, anchor * 0.45)
        );
        double halfWidth = Math.min(
                clamp(arrowLeg * 1.40, 28.0, 650.0),
                maxHalfWidth
        );
        double centerOffset = clamp(
                arrowLeg * 0.38,
                10.0,
                180.0
        );
        double endOffset = clamp(
                arrowLeg * 1.05,
                28.0,
                420.0
        );

        List<GeoPoint> upper = new ArrayList<>();
        List<GeoPoint> lower = new ArrayList<>();

        final int steps = 14;
        for (int i = 0; i <= steps; i++) {
            double t = -1.0 + 2.0 * i / steps;
            double along = anchor + t * halfWidth;

            // Ends farther away, center closer to the line.
            double offset = centerOffset
                    + (endOffset - centerOffset) * t * t;

            upper.add(pointFromAxis(own, bearing, along, offset));
            lower.add(pointFromAxis(own, bearing, along, -offset));
        }

        addLocalItem(makePolyline(
                upper,
                Color.WHITE,
                3.0,
                Shape.BASIC_LINE_STYLE_SOLID
        ));
        addLocalItem(makePolyline(
                lower,
                Color.WHITE,
                3.0,
                Shape.BASIC_LINE_STYLE_SOLID
        ));
    }

    /**
     * Bearing above and distance below the centerline, directly after the
     * central bracket. The transparent carrier line keeps both labels aligned
     * to the selected bearing even when the map is rotated.
     */
    private void addBracketLabel(
            GeoPoint own,
            double bearing,
            double anchor,
            double crossOffset,
            String text) {

        double resolution = getVisualResolution();
        double startGap = clamp(
                resolution * 72.0,
                120.0,
                700.0
        );
        double labelLength = clamp(
                resolution * 145.0,
                240.0,
                1350.0
        );

        List<GeoPoint> pts = new ArrayList<>();
        pts.add(pointFromAxis(
                own,
                bearing,
                anchor + startGap,
                crossOffset
        ));
        pts.add(pointFromAxis(
                own,
                bearing,
                anchor + startGap + labelLength,
                crossOffset
        ));

        Polyline label = makePolyline(
                pts,
                Color.argb(1, 255, 255, 255),
                0.1,
                Shape.BASIC_LINE_STYLE_SOLID
        );
        label.toggleMetaData("labels_on", true);
        label.setLineLabel(text);
        label.setLabelTextSize(16);

        addLocalItem(label);
    }

    private Polyline makePolyline(
            List<GeoPoint> points,
            int color,
            double weight,
            int lineStyle) {

        Polyline line = new Polyline(UUID.randomUUID().toString());
        line.setPoints(wrap(points));
        line.setStrokeColor(color);
        line.setStrokeWeight(weight);
        line.setBasicLineStyle(lineStyle);
        line.setClickable(true);
        line.setEditable(false);
        line.setMovable(false);
        line.setMetaBoolean("nevercot", true);
        line.setMetaBoolean("addToObjList", false);
        return line;
    }

    private void addLocalItem(MapItem item) {
        item.setMetaBoolean("nevercot", true);
        item.setMetaBoolean("addToObjList", false);
        item.setMetaBoolean(META_MRS_OVERLAY, true);
        item.setClickable(true);
        item.setEditable(false);
        item.setMovable(false);
        overlayGroup.addItem(item);
        overlayItems.add(item);
        mapView.getMapEventDispatcher().addMapItemEventListener(item, this);
    }

    private void clearOverlayItems() {
        MapEventDispatcher dispatcher = mapView.getMapEventDispatcher();
        for (MapItem item : overlayItems) {
            dispatcher.removeMapItemEventListener(item, this);
        }
        overlayItems.clear();
        overlayGroup.clearItems();
    }

    private static GeoPointMetaData[] wrap(List<GeoPoint> points) {
        GeoPointMetaData[] wrapped = new GeoPointMetaData[points.size()];
        for (int i = 0; i < points.size(); i++) {
            wrapped[i] = GeoPointMetaData.wrap(points.get(i));
        }
        return wrapped;
    }

    /**
     * Local axis helper:
     * along = meters along target bearing.
     * cross > 0 = left of the target line.
     * cross < 0 = right of the target line.
     */
    private static GeoPoint pointFromAxis(
            GeoPoint origin,
            double bearing,
            double along,
            double cross) {

        GeoPoint onAxis = GeoCalculations.pointAtDistance(
                origin,
                bearing,
                along
        );

        if (Math.abs(cross) < 0.001) {
            return onAxis;
        }

        double crossBearing = normalizeDegrees(
                bearing + (cross > 0 ? -90.0 : 90.0)
        );

        return GeoCalculations.pointAtDistance(
                onAxis,
                crossBearing,
                Math.abs(cross)
        );
    }

    private static double toGridBearing(
            GeoPoint own,
            GeoPoint target,
            double trueBearing) {

        double convergence = ATAKUtilities.computeGridConvergence(own, target);
        if (Double.isNaN(convergence)) {
            convergence = 0.0;
        }

        return normalizeDegrees(trueBearing - convergence);
    }

    private static int degreesToMil(double degrees) {
        int mil = (int) Math.round(
                normalizeDegrees(degrees) * 6400.0 / 360.0
        );
        mil %= 6400;
        if (mil < 0) {
            mil += 6400;
        }
        return mil;
    }

    private static String formatRange(double meters) {
        int rounded = (int) Math.round(meters);

        if (rounded < 1000) {
            return rounded + " m";
        }

        if (rounded % 1000 == 0) {
            return String.format(
                    Locale.GERMANY,
                    "%.0f km",
                    meters / 1000.0
            );
        }

        return String.format(
                Locale.GERMANY,
                "%.1f km",
                meters / 1000.0
        );
    }

    private static String formatTargetDistance(double meters) {
        return String.format(
                Locale.GERMANY,
                "%,.0f m",
                meters
        );
    }

    private String formatTargetCoordinate() {
        if (targetPoint == null || !isUsable(targetPoint.get())) {
            return "Nicht verfügbar";
        }

        return CoordinateFormatUtilities.formatToString(
                targetPoint.get(),
                CoordinateFormat.MGRS
        );
    }

    private double getVisualResolution() {
        double resolution = mapView.getMapResolution();
        if (!Double.isFinite(resolution) || resolution <= 0.0) {
            return 1.0;
        }
        return resolution;
    }

    private static double clamp(
            double value,
            double minimum,
            double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean isUsable(GeoPoint point) {
        return point != null
                && !Double.isNaN(point.getLatitude())
                && !Double.isNaN(point.getLongitude());
    }

    private static double normalizeDegrees(double value) {
        double out = value % 360.0;
        if (out < 0.0) {
            out += 360.0;
        }
        return out;
    }
}
