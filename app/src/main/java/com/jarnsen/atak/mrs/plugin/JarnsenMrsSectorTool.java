package com.jarnsen.atak.mrs.plugin;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.DefaultMapGroup;
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
import com.atakmap.android.util.DragMarkerHelper;
import com.atakmap.coremap.conversions.CoordinateFormat;
import com.atakmap.coremap.conversions.CoordinateFormatUtilities;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.assets.Icon;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.AtakMapView;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Draws the Jarnsen Mrs range sector between two selectable points. Origin and
 * target can each be the ATAK self marker, an entered MGRS coordinate, or a point
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

    private static final double MAX_RANGE_M = MrsCoreLogic.MAX_RANGE_M;
    private static final double RANGE_STEP_M = 500.0;
    private static final String PREF_NEXT_MRS_NUMBER =
            "jarnsen.mrs.next_number";
    private static final String META_MRS_OVERLAY =
            "jarnsen.mrs.overlay";
    private static final String META_MRS_DRAWING_ID =
            "jarnsen.mrs.drawing_id";
    private static final String META_MRS_HANDLE =
            "jarnsen.mrs.handle";
    private static final int MAX_UNDO_STATES = 10;
    private static final long UPDATE_CHECK_INTERVAL_MS =
            24L * 60L * 60L * 1000L;
    private static final String PREF_UPDATE_CHECK_AT =
            "jarnsen.mrs.update.last_check";
    private static final String PREF_AUTO_UPDATE_CHECK =
            "jarnsen.mrs.update.auto_check";
    private static final long RETRY_AFTER_ERROR_MS = 60L * 60L * 1000L;
    private static final String TAG = "JarnsenMrsSectorTool";

    // Fixed 8 km / ±600 NATO mil geometry.
    private static final double HALF_SECTOR_MIL =
            MrsCoreLogic.HALF_SECTOR_MIL;
    private static final double HALF_SECTOR_DEG =
            MrsCoreLogic.HALF_SECTOR_DEG;

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
    private final MapGroup renderGroup;
    private final TextContainer prompt;
    private final List<MapItem> overlayItems = new ArrayList<>();
    private final MrsDrawingStore drawingStore;
    private final LinkedHashMap<String, MrsDrawing> drawings =
            new LinkedHashMap<>();
    private final MrsHistory history = new MrsHistory(MAX_UNDO_STATES);
    private final LinkedHashMap<String, StaticGeometry> geometryCache =
            new LinkedHashMap<String, StaticGeometry>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, StaticGeometry> eldest) {
                    return size() > 32;
                }
            };
    private final LinkedHashMap<String, PointMapItem> linkedMarkerItems =
            new LinkedHashMap<>();

    private boolean selectionActive;
    private boolean targetDragMoved;
    private boolean targetScaleGestureInProgress;
    private boolean editingExistingPoint;
    private int sectorFillColor = COLOR_FILL;
    private double lastVisualResolution = Double.NaN;
    private String drawingLabel;
    private SelectionStage selectionStage = SelectionStage.NONE;
    private AlertDialog activeDialog;
    private String activeDrawingId;
    private String renderDrawingId;
    private MrsDrawing renderDrawing;
    private boolean creatingNewDrawing;
    private boolean originIsSelfSelection;
    private boolean targetIsSelfSelection;
    private String originMarkerUid;
    private String targetMarkerUid;
    private boolean handleDragMode;
    private Marker originHandle;
    private Marker targetHandle;
    private String lastDiagnosticError = "—";
    private String highlightedDrawingId;
    private boolean renderHighlighted;
    private long geometryCacheHits;
    private long geometryCacheMisses;

    private volatile Marker selfMarker;
    // May be read from the thread that reports point changes.
    private volatile PointMapItem originItem;
    private GeoPointMetaData originPoint;
    private volatile PointMapItem targetItem;
    private GeoPointMetaData targetPoint;

    private volatile boolean disposed;
    private final AtomicBoolean redrawScheduled = new AtomicBoolean(false);
    // Markers whose movement changes at least one drawing (rebuilt in
    // refreshLinkedMarkerListeners, read from any thread).
    private volatile Set<PointMapItem> relevantPointItems =
            Collections.emptySet();
    // True while a drawing references a marker UID that is not on the map
    // (yet). Position updates of the own marker then trigger a redraw so the
    // link is found again once the marker appears.
    private volatile boolean hasUnresolvedLinks;

    private final MapEventDispatcher.MapEventDispatchListener
            overlayTapListener = this::handleOverlayTap;

    private void handleOverlayTap(MapEvent event) {
        if (selectionActive
                || handleDragMode
                || activeDialog != null
                || event == null
                || !MapEvent.ITEM_CLICK.equals(event.getType())) {
            return;
        }

        MapItem item = event.getItem();
        if (item == null
                || !item.getMetaBoolean(META_MRS_OVERLAY, false)) {
            return;
        }

        String drawingId = item.getMetaString(META_MRS_DRAWING_ID, null);
        if (drawingId == null || !drawings.containsKey(drawingId)) {
            return;
        }

        highlightDrawing(drawingId);
        mapView.post(() -> {
            if (!selectionActive && activeDialog == null) {
                loadDrawingForEdit(drawingId);
                ToolManagerBroadcastReceiver.getInstance().startTool(
                        TOOL_IDENTIFIER,
                        new Bundle()
                );
            }
        });
    }

    private enum SelectionStage {
        NONE,
        ORIGIN,
        TARGET
    }

    private static final class StaticGeometry {
        final double originLat;
        final double originLon;
        final double bearing;
        final List<GeoPoint> fill;
        final List<GeoPoint> leftBoundary;
        final List<GeoPoint> rightBoundary;
        final List<List<GeoPoint>> arcs;

        StaticGeometry(GeoPoint own, double bearing) {
            this.originLat = own.getLatitude();
            this.originLon = own.getLongitude();
            this.bearing = bearing;

            fill = new ArrayList<>();
            fill.add(own);
            final int fillSteps = 56;
            double start = bearing - HALF_SECTOR_DEG;
            double span = HALF_SECTOR_DEG * 2.0;
            for (int i = 0; i <= fillSteps; i++) {
                double b = start + span * i / fillSteps;
                fill.add(GeoCalculations.pointAtDistance(
                        own,
                        b,
                        MAX_RANGE_M
                ));
            }
            fill.add(own);

            leftBoundary = new ArrayList<>();
            leftBoundary.add(own);
            leftBoundary.add(GeoCalculations.pointAtDistance(
                    own,
                    bearing - HALF_SECTOR_DEG,
                    MAX_RANGE_M
            ));

            rightBoundary = new ArrayList<>();
            rightBoundary.add(own);
            rightBoundary.add(GeoCalculations.pointAtDistance(
                    own,
                    bearing + HALF_SECTOR_DEG,
                    MAX_RANGE_M
            ));

            arcs = new ArrayList<>();
            for (double range = RANGE_STEP_M;
                 range <= MAX_RANGE_M + 0.1;
                 range += RANGE_STEP_M) {
                List<GeoPoint> points = new ArrayList<>();
                final int arcSteps = 42;
                for (int i = 0; i <= arcSteps; i++) {
                    double b = start + span * i / arcSteps;
                    points.add(GeoCalculations.pointAtDistance(
                            own,
                            b,
                            range
                    ));
                }
                arcs.add(points);
            }
        }

        boolean matches(GeoPoint own, double currentBearing) {
            double delta = Math.abs(
                    normalizeDegrees(bearing - currentBearing)
            );
            delta = Math.min(delta, 360.0 - delta);
            return Math.abs(originLat - own.getLatitude()) < 1e-9
                    && Math.abs(originLon - own.getLongitude()) < 1e-9
                    && delta < 1e-7;
        }
    }

    public JarnsenMrsSectorTool(MapView mapView, MapGroup overlayGroup) {
        super(mapView, TOOL_IDENTIFIER);
        this.mapView = mapView;
        this.overlayGroup = overlayGroup;
        this.renderGroup = new DefaultMapGroup("Jarnsen Mrs Zeichnungen");
        this.overlayGroup.addGroup(renderGroup);
        this.prompt = TextContainer.getInstance();
        this.drawingStore = new MrsDrawingStore(mapView.getContext());
        for (MrsDrawing drawing : drawingStore.load()) {
            drawings.put(drawing.id, drawing);
        }
        history.restore(
                drawingStore.loadUndoHistory(),
                drawingStore.loadRedoHistory()
        );
        refreshLinkedMarkerListeners();

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
        mapView.post(this::redraw);
    }

    @Override
    public boolean onToolBegin(Bundle extras) {
        attachSelfListener();
        maybeCheckForUpdate(false);
        if (activeDrawingId != null
                && originPoint != null
                && targetPoint != null) {
            showExistingDrawingDialog();
        } else {
            showWorkspaceMenu();
        }
        return true;
    }

    @Override
    public void onToolEnd() {
        stopMapSelection();
        discardNoOpUndoState();
        removeEditHandles();

        if (activeDialog != null) {
            activeDialog.dismiss();
            activeDialog = null;
        }

        super.onToolEnd();
    }

    @Override
    public void onMapEvent(MapEvent event) {
        String eventType = event.getType();

        if (selectionStage == SelectionStage.TARGET) {
            if (MapEvent.MAP_SCALE.equals(eventType)) {
                // The selection listener replaces ATAK's default MAP_SCALE
                // handler while this temporary listener set is active.
                // Forward the event explicitly so pinch zoom still works.
                targetScaleGestureInProgress = true;
                mapView.getMapTouchController().onScaleEvent(event);
                return;
            }

            if (MapEvent.MAP_PRESS.equals(eventType)
                    || MapEvent.ITEM_PRESS.equals(eventType)) {
                // A fresh one-finger gesture can place or drag the target.
                targetScaleGestureInProgress = false;
                targetDragMoved = false;
            } else if (targetScaleGestureInProgress) {
                // Ignore the remaining finger movement and release from a
                // pinch. A new press starts a separate placement gesture.
                return;
            }
        }

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
        if (item == null || event == null) {
            return;
        }

        if (handleDragMode
                && item.getMetaString(META_MRS_HANDLE, null) != null
                && (MapEvent.ITEM_DRAG_STARTED.equals(event.getType())
                || MapEvent.ITEM_DRAG_CONTINUED.equals(event.getType())
                || MapEvent.ITEM_DRAG_DROPPED.equals(event.getType()))) {
            handleEndpointDrag(item, event);
            return;
        }

        if (!MapEvent.ITEM_CLICK.equals(event.getType())
                || selectionActive
                || activeDialog != null
                || !item.getMetaBoolean(META_MRS_OVERLAY, false)) {
            return;
        }

        String drawingId = item.getMetaString(META_MRS_DRAWING_ID, null);
        if (drawingId != null && drawings.containsKey(drawingId)) {
            loadDrawingForEdit(drawingId);
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

        // Only redraw when this item is actually part of a drawing, and
        // coalesce bursts (GPS ticks) into one redraw on the UI thread.
        if (item == originItem
                || item == targetItem
                || relevantPointItems.contains(item)
                || (hasUnresolvedLinks && item == selfMarker)) {
            scheduleRedraw();
        }
    }

    @Override
    public void onMapMoved(AtakMapView view, boolean animate) {
        if (drawings.isEmpty()
                && (originPoint == null || targetPoint == null)) {
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
        disposed = true;
        if (selectionActive || activeDialog != null) {
            requestEndTool();
        }

        detachEndpointListeners();
        for (PointMapItem marker : linkedMarkerItems.values()) {
            marker.removeOnPointChangedListener(this);
        }
        linkedMarkerItems.clear();

        if (selfMarker != null) {
            selfMarker.removeOnPointChangedListener(this);
            selfMarker = null;
        }

        removeEditHandles();
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

    private void showWorkspaceMenu() {
        activeDrawingId = null;
        highlightedDrawingId = null;
        detachEndpointListeners();
        originPoint = null;
        targetPoint = null;
        drawingLabel = null;
        creatingNewDrawing = false;
        redraw();

        int visibleCount = 0;
        for (MrsDrawing drawing : drawings.values()) {
            if (drawing.visible) {
                visibleCount++;
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle(
                        "Jarnsen Mrs Plugin · "
                                + drawings.size() + " Zeichnung"
                                + (drawings.size() == 1 ? "" : "en")
                                + " · " + visibleCount + " sichtbar"
                )
                .setItems(
                        new String[]{
                                "Neue Zeichnung",
                                "Zeichnungen verwalten",
                                "Alle anzeigen",
                                "Alle ausblenden",
                                "Import / Export",
                                "Rückgängig",
                                "Wiederholen",
                                "Diagnose",
                                "Nach Update suchen",
                                drawingStore.preferences().getBoolean(
                                        PREF_AUTO_UPDATE_CHECK, false)
                                        ? "Auto-Update-Prüfung: Ein"
                                        : "Auto-Update-Prüfung: Aus",
                                "Schließen"
                        },
                        (ignored, which) -> {
                            activeDialog = null;
                            if (which == 0) {
                                startNewSetup();
                            } else if (which == 1) {
                                showDrawingListDialog();
                            } else if (which == 2) {
                                setAllDrawingsVisible(true);
                            } else if (which == 3) {
                                setAllDrawingsVisible(false);
                            } else if (which == 4) {
                                showTransferDialog();
                            } else if (which == 5) {
                                undoWorkspace();
                            } else if (which == 6) {
                                redoWorkspace();
                            } else if (which == 7) {
                                showDiagnosticsDialog();
                            } else if (which == 8) {
                                maybeCheckForUpdate(true);
                            } else if (which == 9) {
                                toggleAutoUpdateCheck();
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

    private void showDrawingListDialog() {
        if (drawings.isEmpty()) {
            Toast.makeText(
                    mapView.getContext(),
                    "Noch keine Zeichnungen vorhanden.",
                    Toast.LENGTH_SHORT
            ).show();
            showWorkspaceMenu();
            return;
        }

        int padding = Math.round(
                12.0f * mapView.getResources().getDisplayMetrics().density
        );

        LinearLayout content = new LinearLayout(mapView.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);

        for (MrsDrawing drawing : new ArrayList<>(drawings.values())) {
            LinearLayout card = new LinearLayout(mapView.getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(0, padding / 2, 0, padding);

            LinearLayout header = new LinearLayout(mapView.getContext());
            header.setOrientation(LinearLayout.HORIZONTAL);

            CheckBox visible = new CheckBox(mapView.getContext());
            visible.setChecked(drawing.visible);
            visible.setText("Sichtbar");
            header.addView(visible);

            TextView title = new TextView(mapView.getContext());
            String label = drawing.label == null
                    || drawing.label.trim().isEmpty()
                    ? "Mrs"
                    : drawing.label.trim();
            SpannableString drawingTitle = new SpannableString(
                    "■  " + label + "\n"
                            + CoordinateFormatUtilities.formatToString(
                                    drawing.targetPoint().get(),
                                    CoordinateFormat.MGRS
                            )
            );
            drawingTitle.setSpan(
                    new ForegroundColorSpan(
                            Color.rgb(
                                    Color.red(drawing.fillColor),
                                    Color.green(drawing.fillColor),
                                    Color.blue(drawing.fillColor)
                            )
                    ),
                    0,
                    1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            title.setText(drawingTitle);
            title.setTextSize(16.0f);
            title.setPadding(padding / 2, 0, 0, 0);
            title.setCompoundDrawablePadding(padding / 2);
            header.addView(
                    title,
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1.0f
                    )
            );
            card.addView(header);

            LinearLayout actions = new LinearLayout(mapView.getContext());
            actions.setOrientation(LinearLayout.HORIZONTAL);

            Button open = new Button(mapView.getContext());
            open.setText("Öffnen");
            actions.addView(
                    open,
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1.0f
                    )
            );

            Button duplicate = new Button(mapView.getContext());
            duplicate.setText("Kopie");
            actions.addView(
                    duplicate,
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1.0f
                    )
            );

            Button delete = new Button(mapView.getContext());
            delete.setText("Löschen");
            actions.addView(
                    delete,
                    new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1.0f
                    )
            );
            card.addView(actions);
            content.addView(card);

            visible.setOnCheckedChangeListener((button, checked) -> {
                if (drawing.visible == checked) {
                    return;
                }
                pushUndoState();
                drawing.visible = checked;
                drawing.updatedAt = System.currentTimeMillis();
                drawingStore.save(drawings.values());
                redraw();
            });

            open.setOnClickListener(button -> {
                if (activeDialog != null) {
                    activeDialog.dismiss();
                    activeDialog = null;
                }
                highlightDrawing(drawing.id);
                loadDrawingForEdit(drawing.id);
                showExistingDrawingDialog();
            });

            duplicate.setOnClickListener(button -> {
                if (activeDialog != null) {
                    activeDialog.dismiss();
                    activeDialog = null;
                }
                loadDrawingForEdit(drawing.id);
                duplicateActiveDrawing();
            });

            delete.setOnClickListener(button -> {
                if (activeDialog != null) {
                    activeDialog.dismiss();
                    activeDialog = null;
                }
                loadDrawingForEdit(drawing.id);
                confirmDeleteActiveDrawing();
            });
        }

        ScrollView scroll = new ScrollView(mapView.getContext());
        scroll.addView(content);

        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle("Zeichnungen verwalten")
                .setView(scroll)
                .setPositiveButton("Neue Zeichnung", (ignored, which) -> {
                    activeDialog = null;
                    startNewSetup();
                })
                .setNegativeButton("Zurück", (ignored, which) -> {
                    activeDialog = null;
                    showWorkspaceMenu();
                })
                .setOnCancelListener(ignored -> {
                    activeDialog = null;
                    showWorkspaceMenu();
                })
                .create();

        activeDialog = dialog;
        dialog.show();
    }

    private void setAllDrawingsVisible(boolean visible) {
        boolean changed = false;
        for (MrsDrawing drawing : drawings.values()) {
            if (drawing.visible != visible) {
                changed = true;
                break;
            }
        }

        if (changed) {
            pushUndoState();
            for (MrsDrawing drawing : drawings.values()) {
                drawing.visible = visible;
                drawing.updatedAt = System.currentTimeMillis();
            }
            drawingStore.save(drawings.values());
            redraw();
        }

        showWorkspaceMenu();
    }

    private void showTransferDialog() {
        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle("Import / Export")
                .setItems(
                        new String[]{
                                "JSON exportieren",
                                "JSON in Zwischenablage kopieren",
                                "JSON-Datei importieren",
                                "Backup wiederherstellen",
                                "Zurück"
                        },
                        (ignored, which) -> {
                            activeDialog = null;
                            if (which == 0) {
                                exportDrawingsToFile();
                            } else if (which == 1) {
                                copyTextToClipboard(
                                        "Jarnsen Mrs JSON",
                                        drawingStore.exportPackage(
                                                drawings.values())
                                );
                                Toast.makeText(
                                        mapView.getContext(),
                                        "JSON in Zwischenablage kopiert.",
                                        Toast.LENGTH_SHORT
                                ).show();
                                showTransferDialog();
                            } else if (which == 2) {
                                showImportFileDialog();
                            } else if (which == 3) {
                                restoreDrawingBackup();
                            } else {
                                showWorkspaceMenu();
                            }
                        }
                )
                .setOnCancelListener(ignored -> {
                    activeDialog = null;
                    showWorkspaceMenu();
                })
                .create();
        activeDialog = dialog;
        dialog.show();
    }

    private File getTransferDirectory() {
        File dir = FileSystemUtils.getItem(
                FileSystemUtils.TOOL_DATA_DIRECTORY
                        + File.separator
                        + "jarnsen-mrs"
        );
        if (!dir.exists() && !dir.mkdirs()) {
            lastDiagnosticError =
                    "Exportordner konnte nicht erstellt werden: "
                            + dir.getAbsolutePath();
        }
        return dir;
    }

    private void exportDrawingsToFile() {
        File dir = getTransferDirectory();
        if (!dir.exists()) {
            Toast.makeText(
                    mapView.getContext(),
                    "Exportordner konnte nicht erstellt werden.",
                    Toast.LENGTH_LONG
            ).show();
            showTransferDialog();
            return;
        }

        String stamp = new SimpleDateFormat(
                "yyyyMMdd-HHmmss",
                Locale.US
        ).format(new Date());
        File file = new File(
                dir,
                "jarnsen-mrs-" + stamp + ".json"
        );

        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(
                    drawingStore.exportPackage(drawings.values())
                            .getBytes(StandardCharsets.UTF_8)
            );
            Toast.makeText(
                    mapView.getContext(),
                    "Exportiert: " + file.getAbsolutePath(),
                    Toast.LENGTH_LONG
            ).show();
        } catch (Exception e) {
            lastDiagnosticError =
                    "JSON-Export: " + e.getClass().getSimpleName();
            Toast.makeText(
                    mapView.getContext(),
                    "JSON-Export fehlgeschlagen.",
                    Toast.LENGTH_LONG
            ).show();
        }

        showTransferDialog();
    }

    private void showImportFileDialog() {
        File dir = getTransferDirectory();
        File[] files = dir.listFiles(
                file -> file.isFile()
                        && file.getName().toLowerCase(Locale.US)
                        .endsWith(".json")
        );

        if (files == null || files.length == 0) {
            Toast.makeText(
                    mapView.getContext(),
                    "Keine JSON-Dateien in " + dir.getAbsolutePath(),
                    Toast.LENGTH_LONG
            ).show();
            showTransferDialog();
            return;
        }

        java.util.Arrays.sort(
                files,
                (left, right) -> Long.compare(
                        right.lastModified(),
                        left.lastModified()
                )
        );

        CharSequence[] names = new CharSequence[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = files[i].getName();
        }

        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle("JSON-Datei importieren")
                .setItems(names, (ignored, which) -> {
                    activeDialog = null;
                    importDrawingFile(files[which]);
                })
                .setNegativeButton("Zurück", (ignored, which) -> {
                    activeDialog = null;
                    showTransferDialog();
                })
                .create();
        activeDialog = dialog;
        dialog.show();
    }

    private void importDrawingFile(File file) {
        try {
            byte[] bytes;
            try (FileInputStream input = new FileInputStream(file)) {
                long length = file.length();
                if (length <= 0L || length > MrsDrawingStore.MAX_IMPORT_BYTES) {
                    throw new IllegalStateException("Datei leer oder zu groß");
                }
                bytes = new byte[(int) length];
                int offset = 0;
                while (offset < bytes.length) {
                    int read = input.read(bytes, offset, bytes.length - offset);
                    if (read < 0) {
                        break;
                    }
                    offset += read;
                }
                if (offset != bytes.length) {
                    throw new IllegalStateException("Datei unvollständig");
                }
            }

            List<MrsDrawing> imported = drawingStore.importPackage(
                    new String(bytes, StandardCharsets.UTF_8)
            );
            if (imported == null) {
                throw new IllegalArgumentException("Ungültiges JSON");
            }
            if (imported.isEmpty()) {
                Toast.makeText(
                        mapView.getContext(),
                        "Die Datei enthält keine Zeichnungen.",
                        Toast.LENGTH_LONG
                ).show();
                showTransferDialog();
                return;
            }
            showImportModeDialog(imported, file.getName());
        } catch (Exception e) {
            lastDiagnosticError =
                    "JSON-Import: " + e.getClass().getSimpleName();
            Toast.makeText(
                    mapView.getContext(),
                    "Import fehlgeschlagen: " + file.getName(),
                    Toast.LENGTH_LONG
            ).show();
            showTransferDialog();
        }
    }

    private void showImportModeDialog(
            List<MrsDrawing> imported,
            String sourceName) {
        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle(
                        "Import: " + sourceName + " · " + imported.size()
                                + " Zeichnung"
                                + (imported.size() == 1 ? "" : "en")
                )
                .setItems(
                        new String[]{
                                "Zusammenführen",
                                "Vorhandene (" + drawings.size()
                                        + ") ersetzen",
                                "Abbrechen"
                        },
                        (ignored, which) -> {
                            activeDialog = null;
                            if (which == 0) {
                                pushUndoState();
                                for (MrsDrawing drawing : imported) {
                                    MrsDrawing value = drawing;
                                    if (drawings.containsKey(value.id)) {
                                        value = value.copyAsNew();
                                        value.label =
                                                (value.label == null
                                                        ? "Mrs"
                                                        : value.label)
                                                        + " (Import)";
                                    }
                                    drawings.put(value.id, value);
                                }
                                geometryCache.clear();
                                drawingStore.save(drawings.values());
                                refreshLinkedMarkerListeners();
                                redraw();
                                showDrawingListDialog();
                            } else if (which == 1) {
                                pushUndoState();
                                drawings.clear();
                                for (MrsDrawing drawing : imported) {
                                    drawings.put(drawing.id, drawing);
                                }
                                geometryCache.clear();
                                drawingStore.save(drawings.values());
                                refreshLinkedMarkerListeners();
                                redraw();
                                showDrawingListDialog();
                            } else {
                                showTransferDialog();
                            }
                        }
                )
                .create();
        activeDialog = dialog;
        dialog.show();
    }

    private void restoreDrawingBackup() {
        String backup = drawingStore.getBackupSnapshot();
        if (backup.isEmpty()) {
            Toast.makeText(
                    mapView.getContext(),
                    "Kein gültiges Backup vorhanden.",
                    Toast.LENGTH_SHORT
            ).show();
            showTransferDialog();
            return;
        }

        pushUndoState();
        restoreWorkspaceSnapshot(backup);
        Toast.makeText(
                mapView.getContext(),
                "Letztes gültiges Backup wiederhergestellt.",
                Toast.LENGTH_SHORT
        ).show();
        showWorkspaceMenu();
    }

    private void copyTextToClipboard(String label, String text) {
        ClipboardManager clipboard =
                (ClipboardManager) mapView.getContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText(label, text)
            );
        }
    }

    private void highlightDrawing(String drawingId) {
        highlightedDrawingId = drawingId;
        redraw();
        mapView.postDelayed(() -> {
            if (drawingId != null
                    && drawingId.equals(highlightedDrawingId)
                    && !drawingId.equals(activeDrawingId)) {
                highlightedDrawingId = null;
                redraw();
            }
        }, 1600L);
    }

    private void loadDrawingForEdit(String drawingId) {
        MrsDrawing d = drawings.get(drawingId);
        if (d == null) {
            return;
        }

        detachEndpointListeners();
        activeDrawingId = d.id;
        creatingNewDrawing = false;
        drawingLabel = d.label;
        sectorFillColor = d.fillColor;
        originIsSelfSelection = d.originSelf;
        targetIsSelfSelection = d.targetSelf;
        originMarkerUid = emptyToNull(d.originMarkerUid);
        targetMarkerUid = emptyToNull(d.targetMarkerUid);

        attachSelfListener();

        if (d.originSelf
                && selfMarker != null
                && isUsable(selfMarker.getPoint())) {
            originItem = selfMarker;
            originPoint = selfMarker.getGeoPointMetaData();
        } else if (findLinkedMarker(originMarkerUid) != null) {
            originItem = findLinkedMarker(originMarkerUid);
            originPoint = originItem.getGeoPointMetaData();
        } else {
            originItem = null;
            originPoint = d.originPoint();
        }

        if (d.targetSelf
                && selfMarker != null
                && isUsable(selfMarker.getPoint())) {
            targetItem = selfMarker;
            targetPoint = selfMarker.getGeoPointMetaData();
        } else if (findLinkedMarker(targetMarkerUid) != null) {
            targetItem = findLinkedMarker(targetMarkerUid);
            targetPoint = targetItem.getGeoPointMetaData();
        } else {
            targetItem = null;
            targetPoint = d.targetPoint();
        }

        if (originItem != null
                && originItem != selfMarker
                && !linkedMarkerItems.containsValue(originItem)) {
            originItem.addOnPointChangedListener(this);
        }
        if (targetItem != null
                && targetItem != selfMarker
                && targetItem != originItem
                && !linkedMarkerItems.containsValue(targetItem)) {
            targetItem.addOnPointChangedListener(this);
        }

        redraw();
    }

    private MrsDrawing drawingFromEditor(String requestedId) {
        String id = requestedId;
        MrsDrawing existing = id == null ? null : drawings.get(id);
        MrsDrawing d = existing == null
                ? new MrsDrawing()
                : existing.copy();

        if (originPoint == null || targetPoint == null
                || !isUsable(originPoint.get())
                || !isUsable(targetPoint.get())) {
            return null;
        }

        GeoPoint origin = originPoint.get();
        GeoPoint target = targetPoint.get();

        d.label = getDrawingLabel();
        d.originLat = origin.getLatitude();
        d.originLon = origin.getLongitude();
        d.targetLat = target.getLatitude();
        d.targetLon = target.getLongitude();
        d.originSelf = originIsSelfSelection;
        d.targetSelf = targetIsSelfSelection;
        d.originMarkerUid = originMarkerUid == null ? "" : originMarkerUid;
        d.targetMarkerUid = targetMarkerUid == null ? "" : targetMarkerUid;
        d.fillColor = sectorFillColor;
        d.fillAlpha = Color.alpha(sectorFillColor);
        d.updatedAt = System.currentTimeMillis();
        return d;
    }

    private void commitEditorToWorkspace() {
        MrsDrawing d = drawingFromEditor(activeDrawingId);
        if (d == null) {
            return;
        }

        if (activeDrawingId == null) {
            activeDrawingId = d.id;
        }

        drawings.put(activeDrawingId, d);
        creatingNewDrawing = false;
        drawingStore.save(drawings.values());
        refreshLinkedMarkerListeners();
        redraw();
    }

    private void pushUndoState() {
        String snapshot = drawingStore.snapshot(drawings.values());
        if (history.push(snapshot)) {
            persistHistory();
        }
    }

    private void persistHistory() {
        drawingStore.saveHistory(
                history.undoSnapshots(),
                history.redoSnapshots()
        );
    }

    private void restoreWorkspaceSnapshot(String snapshot) {
        drawings.clear();
        for (MrsDrawing d : drawingStore.restore(snapshot)) {
            drawings.put(d.id, d);
        }

        geometryCache.clear();
        activeDrawingId = null;
        creatingNewDrawing = false;
        detachEndpointListeners();
        originPoint = null;
        targetPoint = null;
        drawingLabel = null;
        drawingStore.save(drawings.values());
        refreshLinkedMarkerListeners();
        redraw();
    }

    private void undoWorkspace() {
        String previous = history.undo(
                drawingStore.snapshot(drawings.values())
        );
        if (previous == null) {
            Toast.makeText(
                    mapView.getContext(),
                    "Nichts zum Rückgängigmachen.",
                    Toast.LENGTH_SHORT
            ).show();
            showWorkspaceMenu();
            return;
        }

        persistHistory();
        restoreWorkspaceSnapshot(previous);
        showWorkspaceMenu();
    }

    private void redoWorkspace() {
        String next = history.redo(
                drawingStore.snapshot(drawings.values())
        );
        if (next == null) {
            Toast.makeText(
                    mapView.getContext(),
                    "Nichts zum Wiederholen.",
                    Toast.LENGTH_SHORT
            ).show();
            showWorkspaceMenu();
            return;
        }

        persistHistory();
        restoreWorkspaceSnapshot(next);
        showWorkspaceMenu();
    }

    private void deleteActiveDrawing() {
        if (activeDrawingId == null || !drawings.containsKey(activeDrawingId)) {
            showWorkspaceMenu();
            return;
        }

        pushUndoState();
        drawings.remove(activeDrawingId);
        geometryCache.remove(activeDrawingId);
        drawingStore.save(drawings.values());
        refreshLinkedMarkerListeners();
        activeDrawingId = null;
        creatingNewDrawing = false;
        detachEndpointListeners();
        originPoint = null;
        targetPoint = null;
        drawingLabel = null;
        removeEditHandles();
        redraw();
        showWorkspaceMenu();
    }

    private void duplicateActiveDrawing() {
        MrsDrawing source = drawings.get(activeDrawingId);
        if (source == null) {
            return;
        }

        pushUndoState();
        MrsDrawing copy = source.copyAsNew();
        copy.visible = true;
        copy.label = (source.label == null || source.label.trim().isEmpty())
                ? "Mrs Kopie"
                : source.label.trim() + " Kopie";
        drawings.put(copy.id, copy);
        drawingStore.save(drawings.values());
        refreshLinkedMarkerListeners();
        loadDrawingForEdit(copy.id);
        showExistingDrawingDialog();
    }

    private void showDiagnosticsDialog() {
        String message = buildDiagnosticsText();

        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle("Jarnsen Mrs Diagnose")
                .setMessage(message)
                .setPositiveButton("Zurück", (ignored, which) -> {
                    activeDialog = null;
                    showWorkspaceMenu();
                })
                .setNeutralButton("Kopieren", (ignored, which) -> {
                    activeDialog = null;
                    copyTextToClipboard("Jarnsen Mrs Diagnose", message);
                    Toast.makeText(
                            mapView.getContext(),
                            "Diagnose in Zwischenablage kopiert.",
                            Toast.LENGTH_SHORT
                    ).show();
                    showDiagnosticsDialog();
                })
                .setNegativeButton("Exportieren", (ignored, which) -> {
                    activeDialog = null;
                    exportDiagnosticsToFile(message);
                    showDiagnosticsDialog();
                })
                .setOnCancelListener(ignored -> {
                    activeDialog = null;
                    showWorkspaceMenu();
                })
                .create();

        activeDialog = dialog;
        dialog.show();
    }

    private String buildDiagnosticsText() {
        String atakVersion = "unbekannt";
        try {
            atakVersion = mapView.getContext()
                    .getPackageManager()
                    .getPackageInfo("com.atakmap.app", 0)
                    .versionName;
        } catch (Exception ignored) {
        }

        int visibleCount = 0;
        for (MrsDrawing drawing : drawings.values()) {
            if (drawing.visible) {
                visibleCount++;
            }
        }

        String signatureStatus = getSignatureSummary();
        return "Plugin: " + BuildConfig.VERSION_NAME
                + "\nATAK installiert: " + atakVersion
                + "\nATAK Ziel-API: 5.6.0 CIV"
                + "\nAPK-Signatur: " + signatureStatus
                + "\nZeichnungen: " + drawings.size()
                + " (" + visibleCount + " sichtbar)"
                + "\nUndo/Redo: " + history.undoSize()
                + "/" + history.redoSize()
                + "\nGeometrie-Cache Treffer/Neu: "
                + geometryCacheHits + "/" + geometryCacheMisses
                + "\nMax. Reichweite: 8 km (fest)"
                + "\nLetzte MGRS: "
                + (drawingStore.getLastMgrs().isEmpty()
                ? "—"
                : drawingStore.getLastMgrs())
                + "\nBackup: "
                + (drawingStore.getBackupSnapshot().isEmpty()
                ? "nicht vorhanden"
                : "vorhanden")
                + "\nLetzter Fehler: " + lastDiagnosticError;
    }

    private void exportDiagnosticsToFile(String text) {
        File dir = getTransferDirectory();
        if (!dir.exists()) {
            Toast.makeText(
                    mapView.getContext(),
                    "Diagnoseexport fehlgeschlagen.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        String stamp = new SimpleDateFormat(
                "yyyyMMdd-HHmmss",
                Locale.US
        ).format(new Date());
        File file = new File(
                dir,
                "diagnose-" + stamp + ".txt"
        );

        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(
                    mapView.getContext(),
                    "Diagnose exportiert: " + file.getAbsolutePath(),
                    Toast.LENGTH_LONG
            ).show();
        } catch (Exception e) {
            lastDiagnosticError =
                    "Diagnoseexport: " + e.getClass().getSimpleName();
            Toast.makeText(
                    mapView.getContext(),
                    "Diagnoseexport fehlgeschlagen.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private String getSignatureSummary() {
        try {
            android.content.pm.PackageInfo info = mapView.getContext()
                    .getPackageManager()
                    .getPackageInfo(
                            BuildConfig.APPLICATION_ID,
                            MrsPluginUpdateDownloader.signatureFlags()
                    );

            android.content.pm.Signature[] signatures =
                    MrsPluginUpdateDownloader.signaturesOf(info);
            if (signatures == null || signatures.length == 0) {
                return "keine Signatur";
            }

            byte[] encoded = signatures[0].toByteArray();
            X509Certificate certificate = (X509Certificate)
                    CertificateFactory.getInstance("X.509")
                            .generateCertificate(
                                    new ByteArrayInputStream(encoded)
                            );

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(encoded);
            StringBuilder fingerprint = new StringBuilder();
            for (byte b : hash) {
                fingerprint.append(String.format(Locale.US, "%02X", b & 0xFF));
            }

            String subject = certificate.getSubjectX500Principal().getName();
            if (subject.length() > 90) {
                subject = subject.substring(0, 90) + "…";
            }

            return subject + "\nZertifikat SHA-256: " + fingerprint;
        } catch (Exception e) {
            lastDiagnosticError =
                    "Signaturprüfung: " + e.getClass().getSimpleName();
            return "nicht ermittelt";
        }
    }

    private void toggleAutoUpdateCheck() {
        SharedPreferences prefs = drawingStore.preferences();
        boolean enabled = !prefs.getBoolean(PREF_AUTO_UPDATE_CHECK, false);
        prefs.edit().putBoolean(PREF_AUTO_UPDATE_CHECK, enabled).apply();
        Toast.makeText(
                mapView.getContext(),
                enabled
                        ? "Automatische Update-Prüfung ist eingeschaltet."
                        : "Automatische Update-Prüfung ist ausgeschaltet.",
                Toast.LENGTH_SHORT
        ).show();
        showWorkspaceMenu();
    }

    private void maybeCheckForUpdate(boolean userRequested) {
        SharedPreferences prefs = drawingStore.preferences();

        // The automatic check contacts GitHub, so it is opt-in. The manual
        // check in the plugin menu always works.
        if (!userRequested
                && !prefs.getBoolean(PREF_AUTO_UPDATE_CHECK, false)) {
            return;
        }

        long now = System.currentTimeMillis();
        long last = prefs.getLong(PREF_UPDATE_CHECK_AT, 0L);

        if (!userRequested
                && now - last < UPDATE_CHECK_INTERVAL_MS) {
            return;
        }

        if (userRequested) {
            Toast.makeText(
                    mapView.getContext(),
                    "Prüfe GitHub-Release…",
                    Toast.LENGTH_SHORT
            ).show();
        }

        String current = MrsUpdateChecker.stripVersionPrefix(
                BuildConfig.VERSION_NAME
        );
        MrsUpdateChecker.checkAsync(current, result ->
                mapView.post(() -> {
                    // A failed check (offline, rate limit) is retried after
                    // an hour instead of blocking the next 24 hours.
                    long stamp = System.currentTimeMillis();
                    if (result.error != null) {
                        stamp = stamp - UPDATE_CHECK_INTERVAL_MS
                                + RETRY_AFTER_ERROR_MS;
                    }
                    prefs.edit()
                            .putLong(PREF_UPDATE_CHECK_AT, stamp)
                            .apply();
                    handleUpdateResult(result, userRequested);
                })
        );
    }

    private void handleUpdateResult(
            MrsUpdateChecker.Result result,
            boolean userRequested) {
        if (result.updateAvailable) {
            if (!userRequested) {
                Toast.makeText(
                        mapView.getContext(),
                        "Neue Jarnsen-Mrs-Version "
                                + result.latestVersion
                                + " verfügbar. Im Plugin-Menü unter "
                                + "‚Nach Update suchen‘ herunterladen.",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            boolean apkAvailable = result.apkDownloadUrl != null
                    && result.apkSha256 != null
                    && result.apkFileName != null;
            String message = "Version " + result.latestVersion
                    + " ist verfügbar."
                    + (apkAvailable
                    ? " Das TAK.gov-signierte APK wird geprüft und nach "
                    + "/atak/support/apks/custom kopiert. Es wird nicht "
                    + "automatisch installiert."
                    : " Für dieses Release gibt es noch kein geprüftes "
                    + "TAK.gov-APK; du kannst die Release-Seite öffnen.");

            AlertDialog.Builder builder = new AlertDialog.Builder(
                    mapView.getContext()
            ).setTitle("Update verfügbar")
                    .setMessage(message)
                    .setPositiveButton(
                            apkAvailable ? "APK herunterladen" : "Release öffnen",
                            (ignored, which) -> {
                                activeDialog = null;
                                if (apkAvailable) {
                                    downloadUpdateToAtakFolder(result);
                                } else {
                                    openReleasePage(result.releaseUrl);
                                }
                            }
                    )
                    .setNegativeButton("Später", (ignored, which) -> {
                        activeDialog = null;
                        showWorkspaceMenu();
                    });
            if (apkAvailable) {
                builder.setNeutralButton("Release-Seite", (ignored, which) -> {
                    activeDialog = null;
                    openReleasePage(result.releaseUrl);
                });
            }
            AlertDialog dialog = builder.create();
            activeDialog = dialog;
            dialog.show();
            return;
        }

        if (!userRequested) {
            return;
        }

        String text = result.error == null
                ? "Die installierte Version ist aktuell."
                : "Update-Prüfung nicht verfügbar: " + result.error;
        if (result.error != null) {
            lastDiagnosticError = "Update-Prüfung: " + result.error;
        }

        Toast.makeText(
                mapView.getContext(),
                text,
                Toast.LENGTH_LONG
        ).show();
        showWorkspaceMenu();
    }

    private void downloadUpdateToAtakFolder(
            MrsUpdateChecker.Result update) {
        Toast.makeText(
                mapView.getContext(),
                "Lade das geprüfte Update in ATAKs Plugin-Ordner …",
                Toast.LENGTH_LONG
        ).show();
        MrsPluginUpdateDownloader.downloadToAtakFolder(
                mapView.getContext(),
                update,
                result -> {
                    if (result.success) {
                        Toast.makeText(
                                mapView.getContext(),
                                "Update kopiert: " + result.path
                                        + " — jetzt in ATAK unter „Lokales "
                                        + "APK-Verzeichnis“ auswählen.",
                                Toast.LENGTH_LONG
                        ).show();
                    } else {
                        lastDiagnosticError = "Update-Download: "
                                + result.error;
                        Toast.makeText(
                                mapView.getContext(),
                                result.error,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                    showWorkspaceMenu();
                }
        );
    }

    private void openReleasePage(String releaseUrl) {
        try {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(releaseUrl == null
                            ? MrsUpdateChecker.RELEASES_URL
                            : releaseUrl)
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mapView.getContext().startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    mapView.getContext(),
                    "Release-Seite konnte nicht geöffnet werden.",
                    Toast.LENGTH_LONG
            ).show();
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
                                "Eigenposition",
                                "MGRS eingeben",
                                "Auf der Karte wählen"
                        },
                        (ignored, which) -> {
                            activeDialog = null;
                            if (which == 0) {
                                chooseSelfPosition(stage);
                            } else if (which == 1) {
                                showMgrsCoordinateDialog(stage);
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
                .setItems(
                        new String[]{
                                "Bearbeiten",
                                "Neue Zeichnung",
                                "Ziel neu setzen",
                                "Start neu setzen",
                                "Punkte direkt ziehen",
                                "Kopie erstellen",
                                "Entfernen",
                                "Rückgängig",
                                "Wiederholen",
                                "Zur Übersicht",
                                "Schließen"
                        },
                        (ignored, which) -> {
                            activeDialog = null;
                            if (which == 0) {
                                showEditDrawingDialog();
                            } else if (which == 1) {
                                startNewSetup();
                            } else if (which == 2) {
                                pushUndoState();
                                beginEditPoint(SelectionStage.TARGET);
                            } else if (which == 3) {
                                pushUndoState();
                                beginEditPoint(SelectionStage.ORIGIN);
                            } else if (which == 4) {
                                beginHandleEdit();
                            } else if (which == 5) {
                                duplicateActiveDrawing();
                            } else if (which == 6) {
                                confirmDeleteActiveDrawing();
                            } else if (which == 7) {
                                undoWorkspace();
                            } else if (which == 8) {
                                redoWorkspace();
                            } else if (which == 9) {
                                showWorkspaceMenu();
                            } else {
                                closeTool();
                            }
                        }
                )
                .setOnCancelListener(ignored -> {
                    activeDialog = null;
                    showWorkspaceMenu();
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
                                "Darstellung",
                                "Transparenz ändern",
                                "Punkte direkt ziehen",
                                "Zurück",
                                "Schließen"
                        },
                        (ignored, which) -> {
                            activeDialog = null;
                            if (which == 0) {
                                pushUndoState();
                                beginEditPoint(SelectionStage.ORIGIN);
                            } else if (which == 1) {
                                pushUndoState();
                                beginEditPoint(SelectionStage.TARGET);
                            } else if (which == 2) {
                                pushUndoState();
                                editingExistingPoint = true;
                                showMgrsCoordinateDialog(
                                        SelectionStage.ORIGIN);
                            } else if (which == 3) {
                                pushUndoState();
                                editingExistingPoint = true;
                                showMgrsCoordinateDialog(
                                        SelectionStage.TARGET);
                            } else if (which == 4) {
                                pushUndoState();
                                showLabelDialog(false);
                            } else if (which == 5) {
                                pushUndoState();
                                showColorSelectionDialog(false);
                            } else if (which == 6) {
                                showDisplaySettingsDialog();
                            } else if (which == 7) {
                                showTransparencyDialog();
                            } else if (which == 8) {
                                beginHandleEdit();
                            } else if (which == 9) {
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

    private void beginHandleEdit() {
        if (activeDrawingId == null
                || originPoint == null
                || targetPoint == null) {
            showExistingDrawingDialog();
            return;
        }

        pushUndoState();
        removeEditHandles();
        handleDragMode = true;
        editingExistingPoint = true;

        originHandle = createEditHandle(
                "Start",
                originPoint,
                "origin",
                COLOR_PRIMARY
        );
        targetHandle = createEditHandle(
                "Ziel",
                targetPoint,
                "target",
                COLOR_TARGET
        );

        prompt.displayPrompt(
                "Start oder Ziel anfassen, ziehen und loslassen"
        );
    }

    private Marker createEditHandle(
            String title,
            GeoPointMetaData point,
            String role,
            int color) {

        Marker marker = new Marker(
                point,
                UUID.randomUUID().toString()
        );
        marker.setTitle(title);
        marker.setType("shape_marker");
        marker.setShowLabel(true);
        marker.setAlwaysShowText(true);
        marker.setLabelTextSize(15);
        marker.setColor(color);
        setHandleIcon(marker, color, 52);
        marker.setClickable(true);
        marker.setEditable(true);
        marker.setMovable(true);
        marker.setEditing(true);
        marker.setMetaBoolean("drag", true);
        marker.setMetaBoolean("movable", true);
        marker.setMetaBoolean("removable", false);
        marker.setMetaBoolean("nevercot", true);
        marker.setMetaBoolean("addToObjList", false);
        marker.setMetaString(META_MRS_HANDLE, role);
        marker.setMetaString(META_MRS_DRAWING_ID, activeDrawingId);
        overlayGroup.addItem(marker);
        mapView.getMapEventDispatcher().addMapItemEventListener(
                marker,
                this
        );
        return marker;
    }

    private void setHandleIcon(
            Marker marker,
            int color,
            int size) {
        String uri = "android.resource://"
                + BuildConfig.APPLICATION_ID
                + "/"
                + R.drawable.mrs_handle;
        Icon icon = new Icon.Builder()
                .setImageUri(Icon.STATE_DEFAULT, uri)
                .setColor(Icon.STATE_DEFAULT, color)
                .setSize(size, size)
                .setAnchor(Icon.ANCHOR_CENTER, Icon.ANCHOR_CENTER)
                .build();
        marker.setIcon(icon);
    }

    private void handleEndpointDrag(MapItem item, MapEvent event) {
        if (!(item instanceof PointMapItem)
                || event == null
                || event.getPointF() == null) {
            return;
        }

        String role = item.getMetaString(META_MRS_HANDLE, null);
        if (role == null) {
            return;
        }

        if (item instanceof Marker) {
            Marker handle = (Marker) item;
            if (MapEvent.ITEM_DRAG_STARTED.equals(event.getType())) {
                setHandleIcon(
                        handle,
                        "origin".equals(role)
                                ? COLOR_PRIMARY
                                : COLOR_TARGET,
                        76
                );
            }
            if (MapEvent.ITEM_DRAG_STARTED.equals(event.getType())
                    || MapEvent.ITEM_DRAG_CONTINUED.equals(event.getType())) {
                DragMarkerHelper.getInstance().updateWidget(item);
            }
        }

        GeoPointMetaData moved = mapView.inverseWithElevation(
                event.getPointF().x,
                event.getPointF().y
        );
        if (moved == null || !isUsable(moved.get())) {
            return;
        }

        if ("origin".equals(role)) {
            if (!isDifferentFromTarget(moved.get())) {
                if (MapEvent.ITEM_DRAG_DROPPED.equals(event.getType())) {
                    showSamePointWarning();
                }
                return;
            }
        } else if (!isDifferentFromOrigin(moved.get())) {
            if (MapEvent.ITEM_DRAG_DROPPED.equals(event.getType())) {
                showSamePointWarning();
            }
            return;
        }

        PointMapItem pointItem = (PointMapItem) item;
        pointItem.setPoint(moved);

        // A dragged endpoint becomes a fixed point: release the link to the
        // self marker / ATAK marker, otherwise its next position update
        // would move the endpoint back.
        if ("origin".equals(role)) {
            detachOriginListener();
            originPoint = moved;
            originIsSelfSelection = false;
            originMarkerUid = null;
        } else {
            detachTargetListener();
            targetPoint = moved;
            targetIsSelfSelection = false;
            targetMarkerUid = null;
        }

        redraw();

        if (MapEvent.ITEM_DRAG_DROPPED.equals(event.getType())) {
            DragMarkerHelper.getInstance().hideWidget();
            commitEditorToWorkspace();
            removeEditHandles();
            editingExistingPoint = false;
            showExistingDrawingDialog();
        }
    }

    private void removeEditHandles() {
        handleDragMode = false;
        prompt.closePrompt();
        MapEventDispatcher dispatcher = mapView.getMapEventDispatcher();

        if (originHandle != null) {
            originHandle.setEditing(false);
            dispatcher.removeMapItemEventListener(originHandle, this);
            if (originHandle.getGroup() != null) {
                originHandle.removeFromGroup();
            }
            originHandle = null;
        }

        if (targetHandle != null) {
            targetHandle.setEditing(false);
            dispatcher.removeMapItemEventListener(targetHandle, this);
            if (targetHandle.getGroup() != null) {
                targetHandle.removeFromGroup();
            }
            targetHandle = null;
        }
    }

    private void showDisplaySettingsDialog() {
        MrsDrawing d = drawings.get(activeDrawingId);
        if (d == null) {
            showEditDrawingDialog();
            return;
        }

        String[] labels = {
                "500-m-Zwischenbögen",
                "1-km-Bögen",
                "Entfernungsbeschriftungen",
                ")(-Klammer",
                "Zielkreuz",
                "Sektorfüllung"
        };
        boolean[] checked = {
                d.showHalfKm,
                d.showKm,
                d.showRangeLabels,
                d.showBracket,
                d.showTargetMarker,
                d.showFill
        };

        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle(getDrawingLabel() + " – Darstellung")
                .setMultiChoiceItems(
                        labels,
                        checked,
                        (ignored, which, isChecked) ->
                                checked[which] = isChecked
                )
                .setPositiveButton("Übernehmen", (ignored, which) -> {
                    activeDialog = null;
                    pushUndoState();
                    d.showHalfKm = checked[0];
                    d.showKm = checked[1];
                    d.showRangeLabels = checked[2];
                    d.showBracket = checked[3];
                    d.showTargetMarker = checked[4];
                    d.showFill = checked[5];
                    d.updatedAt = System.currentTimeMillis();
                    drawingStore.save(drawings.values());
                    redraw();
                    showEditDrawingDialog();
                })
                .setNegativeButton("Abbrechen", (ignored, which) -> {
                    activeDialog = null;
                    showEditDrawingDialog();
                })
                .create();

        activeDialog = dialog;
        dialog.show();
    }

    private void showTransparencyDialog() {
        MrsDrawing d = drawings.get(activeDrawingId);
        if (d == null) {
            showEditDrawingDialog();
            return;
        }

        final int[] percentages = {15, 30, 50, 70};
        String[] labels = {"15 %", "30 %", "50 %", "70 %"};

        int currentPercent = Math.round(
                Color.alpha(d.fillColor) * 100.0f / 255.0f
        );
        int selected = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < percentages.length; i++) {
            int distance = Math.abs(percentages[i] - currentPercent);
            if (distance < bestDistance) {
                bestDistance = distance;
                selected = i;
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle(getDrawingLabel() + " – Transparenz")
                .setSingleChoiceItems(labels, selected, (ignored, which) -> {
                    activeDialog = null;
                    pushUndoState();
                    int alpha = Math.round(
                            percentages[which] * 255.0f / 100.0f
                    );
                    d.fillColor = Color.argb(
                            alpha,
                            Color.red(d.fillColor),
                            Color.green(d.fillColor),
                            Color.blue(d.fillColor)
                    );
                    d.fillAlpha = alpha;
                    d.updatedAt = System.currentTimeMillis();
                    sectorFillColor = d.fillColor;
                    drawingStore.save(drawings.values());
                    redraw();
                    ignored.dismiss();
                    showEditDrawingDialog();
                })
                .setNegativeButton("Abbrechen", (ignored, which) -> {
                    activeDialog = null;
                    showEditDrawingDialog();
                })
                .create();

        activeDialog = dialog;
        dialog.show();
    }

    private void startNewSetup() {
        editingExistingPoint = false;
        creatingNewDrawing = true;
        activeDrawingId = null;
        resetEndpoints();
        drawingLabel = null;
        sectorFillColor = COLOR_FILL;
        originIsSelfSelection = false;
        targetIsSelfSelection = false;
        originMarkerUid = null;
        targetMarkerUid = null;
        showPointSourceDialog(SelectionStage.ORIGIN);
    }

    private void beginEditPoint(SelectionStage stage) {
        editingExistingPoint = true;
        showPointSourceDialog(stage);
    }

    private void confirmDeleteActiveDrawing() {
        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle(getDrawingLabel() + " entfernen?")
                .setMessage("Die Zeichnung kann anschließend über Rückgängig wiederhergestellt werden.")
                .setPositiveButton("Entfernen", (ignored, which) -> {
                    activeDialog = null;
                    deleteActiveDrawing();
                })
                .setNegativeButton("Abbrechen", (ignored, which) -> {
                    activeDialog = null;
                    showExistingDrawingDialog();
                })
                .create();
        activeDialog = dialog;
        dialog.show();
    }

    private void chooseSelfPosition(SelectionStage stage) {
        attachSelfListener();
        if (selfMarker == null || !isUsable(selfMarker.getPoint())) {
            Toast.makeText(
                    mapView.getContext(),
                    "Eigenposition ist noch nicht verfügbar.",
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

        CheckBox followSelf = new CheckBox(mapView.getContext());
        followSelf.setText("Eigenposition folgen");
        followSelf.setChecked(false);
        followSelf.setMinHeight(Math.round(
                48.0f * mapView.getResources().getDisplayMetrics().density
        ));
        int horizontalPadding = Math.round(
                20.0f * mapView.getResources().getDisplayMetrics().density
        );
        followSelf.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        TextView explanation = new TextView(mapView.getContext());
        explanation.setText(
                "Ohne Häkchen wird die aktuelle Eigenposition als fester Punkt "
                        + "übernommen. Mit Häkchen folgt die Zeichnung späteren "
                        + "Positionsänderungen."
        );
        LinearLayout content = new LinearLayout(mapView.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(
                12.0f * mapView.getResources().getDisplayMetrics().density
        );
        content.setPadding(pad, pad, pad, 0);
        content.addView(explanation);
        content.addView(followSelf);
        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle((stage == SelectionStage.ORIGIN
                        ? "Startpunkt"
                        : "Zielpunkt") + " – Eigenposition")
                .setView(content)
                .setPositiveButton("Übernehmen", (ignored, which) -> {
                    activeDialog = null;
                    applySelfPosition(stage, followSelf.isChecked());
                })
                .setNegativeButton("Zurück", (ignored, which) -> {
                    activeDialog = null;
                    showPointSourceDialog(stage);
                })
                .setOnCancelListener(ignored -> {
                    activeDialog = null;
                    showPointSourceDialog(stage);
                })
                .create();

        activeDialog = dialog;
        dialog.show();
    }

    private void applySelfPosition(
            SelectionStage stage,
            boolean followSelf) {
        attachSelfListener();
        if (selfMarker == null || !isUsable(selfMarker.getPoint())) {
            Toast.makeText(
                    mapView.getContext(),
                    "Eigenposition ist nicht mehr verfügbar.",
                    Toast.LENGTH_SHORT
            ).show();
            showPointSourceDialog(stage);
            return;
        }

        GeoPoint current = selfMarker.getPoint();
        if (stage == SelectionStage.TARGET
                && !isDifferentFromOrigin(current)) {
            showSamePointWarning();
            showPointSourceDialog(stage);
            return;
        }

        GeoPointMetaData selected = followSelf
                ? selfMarker.getGeoPointMetaData()
                : GeoPointMetaData.wrap(new GeoPoint(
                        current.getLatitude(),
                        current.getLongitude()
                ));
        MapItem boundItem = followSelf ? selfMarker : null;

        if (stage == SelectionStage.ORIGIN) {
            setOrigin(selected, boundItem);
            if (editingExistingPoint) {
                finishPointEdit();
            } else {
                showPointSourceDialog(SelectionStage.TARGET);
            }
        } else {
            setTarget(selected, boundItem);
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

        TextView recognized = new TextView(mapView.getContext());
        recognized.setTextSize(13.0f);

        mgrs.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String value = normalizeMgrs(editable.toString());
                if (value.length() < 5) {
                    mgrs.setError(null);
                    recognized.setText("");
                    return;
                }

                try {
                    GeoPoint point = CoordinateFormatUtilities.convert(
                            value,
                            CoordinateFormat.MGRS
                    );
                    if (isUsable(point)) {
                        mgrs.setError(null);
                        recognized.setText(
                                "Erkannt: "
                                        + CoordinateFormatUtilities
                                        .formatToString(
                                                point,
                                                CoordinateFormat.MGRS
                                        )
                        );
                    } else {
                        recognized.setText("");
                        mgrs.setError("MGRS ungültig");
                    }
                } catch (Exception ignored) {
                    recognized.setText("");
                    mgrs.setError("MGRS noch unvollständig/ungültig");
                }
            }
        });

        GeoPointMetaData current = stage == SelectionStage.ORIGIN
                ? originPoint
                : targetPoint;
        if (current != null && isUsable(current.get())) {
            mgrs.setText(CoordinateFormatUtilities.formatToString(
                    current.get(),
                    CoordinateFormat.MGRS
            ));
        } else if (!drawingStore.getLastMgrs().isEmpty()) {
            mgrs.setText(drawingStore.getLastMgrs());
        }
        mgrs.setSelection(mgrs.getText().length());

        int padding = Math.round(
                20.0f * mapView.getResources().getDisplayMetrics().density
        );
        LinearLayout holder = new LinearLayout(mapView.getContext());
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.setPadding(padding, 0, padding, 0);
        holder.addView(mgrs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        holder.addView(recognized, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog dialog = new AlertDialog.Builder(mapView.getContext())
                .setTitle((stage == SelectionStage.ORIGIN
                        ? "Startpunkt"
                        : "Zielpunkt") + " – MGRS")
                .setMessage(
                        "MGRS kann direkt eingegeben oder aus der "
                                + "Zwischenablage eingefügt werden."
                )
                .setView(holder)
                .setPositiveButton("Übernehmen", null)
                .setNeutralButton("Einfügen", null)
                .setNegativeButton("Zurück", (ignored, which) -> {
                    activeDialog = null;
                    if (editingExistingPoint) {
                        discardNoOpUndoState();
                        editingExistingPoint = false;
                        showEditDrawingDialog();
                    } else {
                        showPointSourceDialog(stage);
                    }
                })
                .setOnCancelListener(ignored -> {
                    activeDialog = null;
                    cancelPointSelection();
                })
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setOnClickListener(button -> {
                        ClipboardManager clipboard =
                                (ClipboardManager) mapView.getContext()
                                        .getSystemService(
                                                Context.CLIPBOARD_SERVICE);
                        if (clipboard == null
                                || !clipboard.hasPrimaryClip()
                                || clipboard.getPrimaryClip() == null
                                || clipboard.getPrimaryClip()
                                .getItemCount() == 0) {
                            Toast.makeText(
                                    mapView.getContext(),
                                    "Zwischenablage ist leer.",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        CharSequence text = clipboard.getPrimaryClip()
                                .getItemAt(0)
                                .coerceToText(mapView.getContext());
                        if (text != null) {
                            mgrs.setText(normalizeMgrs(text.toString()));
                            mgrs.setSelection(mgrs.getText().length());
                        }
                    });

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(button -> {
                        GeoPoint entered = parseMgrsCoordinate(mgrs);
                        if (entered == null) {
                            return;
                        }

                        if (stage == SelectionStage.TARGET
                                && !isDifferentFromOrigin(entered)) {
                            showSamePointWarning();
                            return;
                        }

                        String normalized = normalizeMgrs(
                                mgrs.getText().toString()
                        );
                        drawingStore.saveLastMgrs(normalized);

                        dialog.dismiss();
                        activeDialog = null;
                        GeoPointMetaData point =
                                GeoPointMetaData.wrap(entered);
                        if (stage == SelectionStage.ORIGIN) {
                            setOrigin(point, null);
                            if (editingExistingPoint) {
                                finishPointEdit();
                            } else {
                                showPointSourceDialog(
                                        SelectionStage.TARGET);
                            }
                        } else {
                            setTarget(point, null);
                            if (editingExistingPoint) {
                                finishPointEdit();
                            } else {
                                finishSetup();
                            }
                        }
                    });
        });

        activeDialog = dialog;
        dialog.show();
    }

    private GeoPoint parseMgrsCoordinate(EditText mgrs) {
        String value = normalizeMgrs(mgrs.getText().toString());
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
            mgrs.setText(value);
            mgrs.setSelection(value.length());
            return point;
        } catch (Exception ignored) {
            lastDiagnosticError = "Ungültige MGRS-Eingabe: " + value;
            Toast.makeText(
                    mapView.getContext(),
                    "MGRS-Koordinate ist nicht gültig.",
                    Toast.LENGTH_SHORT
            ).show();
            return null;
        }
    }

    private static String normalizeMgrs(String value) {
        return MrsCoreLogic.normalizeMgrsInput(value);
    }

    private void beginMapSelection(SelectionStage stage) {
        stopMapSelection();
        selectionStage = stage;
        targetScaleGestureInProgress = false;

        MapEventDispatcher dispatcher = mapView.getMapEventDispatcher();
        dispatcher.pushListeners();
        dispatcher.clearListeners(MapEvent.ITEM_CLICK);
        dispatcher.clearListeners(MapEvent.MAP_CLICK);
        dispatcher.addMapEventListener(MapEvent.ITEM_CLICK, this);
        dispatcher.addMapEventListener(MapEvent.MAP_CLICK, this);

        if (stage == SelectionStage.TARGET) {
            // MAP_SCROLL is ATAK's default camera-pan handler. Remove it only
            // for target placement, while keeping the MAP_DRAW preview events.
            dispatcher.clearListeners(MapEvent.MAP_SCROLL);
            dispatcher.clearListeners(MapEvent.MAP_SCALE);
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
            dispatcher.addMapEventListener(MapEvent.MAP_SCALE, this);
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
        targetScaleGestureInProgress = false;
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
        commitEditorToWorkspace();
        redraw();
        showExistingDrawingDialog();
    }

    private void cancelPointSelection() {
        stopMapSelection();
        if (editingExistingPoint) {
            discardNoOpUndoState();
            editingExistingPoint = false;
            showExistingDrawingDialog();
        } else {
            closeTool();
        }
    }

    private void closeTool() {
        stopMapSelection();
        discardNoOpUndoState();
        removeEditHandles();
        editingExistingPoint = false;
        creatingNewDrawing = false;
        activeDrawingId = null;
        highlightedDrawingId = null;
        detachEndpointListeners();
        originPoint = null;
        targetPoint = null;
        drawingLabel = null;
        originIsSelfSelection = false;
        targetIsSelfSelection = false;
        originMarkerUid = null;
        targetMarkerUid = null;
        redraw();
        ToolManagerBroadcastReceiver.getInstance().endCurrentTool();
    }

    private void showLabelDialog(boolean continueToColor) {
        ensureDrawingLabel();

        EditText label = new EditText(mapView.getContext());
        label.setHint("Optional, z. B. Mrs 1");
        label.setSingleLine(true);
        label.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        label.setImeOptions(EditorInfo.IME_ACTION_DONE);
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
                        commitEditorToWorkspace();
                        showExistingDrawingDialog();
                    }
                })
                .setNegativeButton("Standard", (ignored, which) -> {
                    activeDialog = null;
                    redraw();
                    if (continueToColor) {
                        showColorSelectionDialog(true);
                    } else {
                        commitEditorToWorkspace();
                        showExistingDrawingDialog();
                    }
                })
                .setOnCancelListener(ignored -> {
                    activeDialog = null;
                    redraw();
                    if (continueToColor) {
                        showColorSelectionDialog(true);
                    } else {
                        discardNoOpUndoState();
                        showExistingDrawingDialog();
                    }
                })
                .create();

        activeDialog = dialog;
        dialog.show();
        label.requestFocus();
        label.post(() -> {
            InputMethodManager keyboard = (InputMethodManager)
                    mapView.getContext().getSystemService(
                            Context.INPUT_METHOD_SERVICE
                    );
            if (keyboard != null) {
                keyboard.showSoftInput(
                        label,
                        InputMethodManager.SHOW_IMPLICIT
                );
            }
        });
    }

    private void ensureDrawingLabel() {
        if (drawingLabel != null && !drawingLabel.trim().isEmpty()) {
            return;
        }

        SharedPreferences prefs = drawingStore.preferences();
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
                        pushUndoState();
                        commitEditorToWorkspace();
                        showExistingDrawingDialog();
                    } else {
                        commitEditorToWorkspace();
                        showExistingDrawingDialog();
                    }
                })
                .setNeutralButton("Standard", (ignored, which) -> {
                    activeDialog = null;
                    sectorFillColor = COLOR_FILL;
                    redraw();
                    if (closeWhenDone) {
                        pushUndoState();
                        commitEditorToWorkspace();
                        showExistingDrawingDialog();
                    } else {
                        commitEditorToWorkspace();
                        showExistingDrawingDialog();
                    }
                })
                .setOnCancelListener(ignored -> {
                    activeDialog = null;
                    if (closeWhenDone) {
                        pushUndoState();
                        commitEditorToWorkspace();
                        showExistingDrawingDialog();
                    } else {
                        discardNoOpUndoState();
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

    private boolean isDifferentFromTarget(GeoPoint candidate) {
        if (targetPoint == null || !isUsable(targetPoint.get())) {
            return true;
        }
        double distance = targetPoint.get().distanceTo(candidate);
        return Double.isNaN(distance) || distance >= 1.0;
    }

    private void resetEndpoints() {
        discardNoOpUndoState();
        detachEndpointListeners();
        originPoint = null;
        targetPoint = null;
        originIsSelfSelection = false;
        targetIsSelfSelection = false;
        originMarkerUid = null;
        targetMarkerUid = null;
        editingExistingPoint = false;
        lastVisualResolution = Double.NaN;
        removeEditHandles();
        redraw();
    }

    private void discardNoOpUndoState() {
        if (history.discardLastUndoIfEquals(
                drawingStore.snapshot(drawings.values()))) {
            persistHistory();
        }
    }

    private void setOrigin(GeoPointMetaData point, MapItem item) {
        detachOriginListener();
        originPoint = point;
        originIsSelfSelection = item != null && item == selfMarker;
        originMarkerUid = item instanceof PointMapItem
                && item != selfMarker
                ? item.getUID()
                : null;

        if (item instanceof PointMapItem) {
            originItem = (PointMapItem) item;
            originPoint = originItem.getGeoPointMetaData();
            if (originItem != selfMarker
                    && !linkedMarkerItems.containsValue(originItem)) {
                originItem.addOnPointChangedListener(this);
            }
        }
    }

    private void setTarget(GeoPointMetaData point, MapItem item) {
        detachTargetListener();

        targetPoint = point;
        targetIsSelfSelection = item != null && item == selfMarker;
        targetMarkerUid = item instanceof PointMapItem
                && item != selfMarker
                ? item.getUID()
                : null;

        if (item instanceof PointMapItem) {
            targetItem = (PointMapItem) item;
            targetPoint = targetItem.getGeoPointMetaData();
            if (targetItem != selfMarker
                    && !linkedMarkerItems.containsValue(targetItem)) {
                targetItem.addOnPointChangedListener(this);
            }
        }

        redraw();
    }

    private void detachTargetListener() {
        if (targetItem != null) {
            if (targetItem != selfMarker
                    && !linkedMarkerItems.containsValue(targetItem)) {
                targetItem.removeOnPointChangedListener(this);
            }
            targetItem = null;
        }
    }

    private void detachOriginListener() {
        if (originItem != null) {
            if (originItem != selfMarker
                    && !linkedMarkerItems.containsValue(originItem)) {
                originItem.removeOnPointChangedListener(this);
            }
            originItem = null;
        }
    }

    private void detachEndpointListeners() {
        detachOriginListener();
        detachTargetListener();
    }

    private PointMapItem findLinkedMarker(String uid) {
        String value = emptyToNull(uid);
        if (value == null) {
            return null;
        }
        MapItem item = mapView.getRootGroup().deepFindUID(value);
        return item instanceof PointMapItem
                ? (PointMapItem) item
                : null;
    }

    private void refreshLinkedMarkerListeners() {
        LinkedHashMap<String, PointMapItem> desired =
                new LinkedHashMap<>();
        boolean unresolved = false;
        for (MrsDrawing drawing : drawings.values()) {
            PointMapItem origin = findLinkedMarker(drawing.originMarkerUid);
            if (origin == null
                    && emptyToNull(drawing.originMarkerUid) != null) {
                unresolved = true;
            }
            if (origin != null && origin != selfMarker) {
                desired.put(origin.getUID(), origin);
            }
            PointMapItem target = findLinkedMarker(drawing.targetMarkerUid);
            if (target == null
                    && emptyToNull(drawing.targetMarkerUid) != null) {
                unresolved = true;
            }
            if (target != null && target != selfMarker) {
                desired.put(target.getUID(), target);
            }
        }
        hasUnresolvedLinks = unresolved;

        for (Map.Entry<String, PointMapItem> entry
                : new ArrayList<>(linkedMarkerItems.entrySet())) {
            PointMapItem replacement = desired.get(entry.getKey());
            if (replacement != entry.getValue()) {
                entry.getValue().removeOnPointChangedListener(this);
                linkedMarkerItems.remove(entry.getKey());
            }
        }

        for (Map.Entry<String, PointMapItem> entry : desired.entrySet()) {
            if (linkedMarkerItems.containsKey(entry.getKey())) {
                continue;
            }
            PointMapItem marker = entry.getValue();
            if (marker != originItem && marker != targetItem) {
                marker.addOnPointChangedListener(this);
            }
            linkedMarkerItems.put(entry.getKey(), marker);
        }

        rebuildRelevantItems();
    }

    private static String emptyToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private void redraw() {
        if (disposed) {
            return;
        }
        clearOverlayItems();
        attachSelfListener();
        refreshLinkedMarkerListeners();

        for (MrsDrawing d : drawings.values()) {
            if (!d.visible
                    && (activeDrawingId == null
                    || !activeDrawingId.equals(d.id))) {
                continue;
            }
            if (activeDrawingId != null
                    && activeDrawingId.equals(d.id)
                    && originPoint != null
                    && targetPoint != null) {
                continue;
            }
            try {
                renderStoredDrawing(d);
            } catch (RuntimeException e) {
                reportRenderFailure(d, e);
            }
        }

        if (originPoint != null
                && targetPoint != null
                && isUsable(originPoint.get())
                && isUsable(targetPoint.get())) {

            MrsDrawing editor;
            if (activeDrawingId != null
                    && drawings.containsKey(activeDrawingId)) {
                editor = drawings.get(activeDrawingId).copy();
            } else {
                editor = new MrsDrawing("__draft__");
            }

            GeoPoint origin = originPoint.get();
            GeoPoint target = targetPoint.get();
            editor.label = getDrawingLabel();
            editor.originLat = origin.getLatitude();
            editor.originLon = origin.getLongitude();
            editor.targetLat = target.getLatitude();
            editor.targetLon = target.getLongitude();
            editor.originSelf = originIsSelfSelection;
            editor.targetSelf = targetIsSelfSelection;
            editor.fillColor = sectorFillColor;
            editor.fillAlpha = Color.alpha(sectorFillColor);

            try {
                renderGeometry(editor, origin, target);
            } catch (RuntimeException e) {
                reportRenderFailure(editor, e);
            }
        }
    }

    private void reportRenderFailure(MrsDrawing d, RuntimeException e) {
        resetRenderState();
        lastDiagnosticError = "Darstellung "
                + (d == null ? "?" : d.id) + ": "
                + e.getClass().getSimpleName();
        Log.e(TAG, "Zeichnung konnte nicht dargestellt werden", e);
    }

    private void resetRenderState() {
        renderDrawing = null;
        renderDrawingId = null;
        renderHighlighted = false;
    }

    private void scheduleRedraw() {
        if (!redrawScheduled.compareAndSet(false, true)) {
            return;
        }
        mapView.post(() -> {
            redrawScheduled.set(false);
            redraw();
        });
    }

    private PointMapItem findLinkedMarkerCached(String uid) {
        String value = emptyToNull(uid);
        if (value == null) {
            return null;
        }
        PointMapItem cached = linkedMarkerItems.get(value);
        return cached != null ? cached : findLinkedMarker(value);
    }

    private void rebuildRelevantItems() {
        Set<PointMapItem> items = Collections.newSetFromMap(
                new IdentityHashMap<PointMapItem, Boolean>());
        items.addAll(linkedMarkerItems.values());
        if (selfMarker != null) {
            for (MrsDrawing d : drawings.values()) {
                if (d.originSelf || d.targetSelf) {
                    items.add(selfMarker);
                    break;
                }
            }
        }
        relevantPointItems = items;
    }

    private void renderStoredDrawing(MrsDrawing d) {
        GeoPoint origin;
        GeoPoint target;
        PointMapItem originMarker = findLinkedMarkerCached(d.originMarkerUid);
        PointMapItem targetMarker = findLinkedMarkerCached(d.targetMarkerUid);

        if (d.originSelf
                && selfMarker != null
                && isUsable(selfMarker.getPoint())) {
            origin = selfMarker.getPoint();
        } else if (originMarker != null
                && isUsable(originMarker.getPoint())) {
            origin = originMarker.getPoint();
        } else {
            origin = d.originPoint().get();
        }

        if (d.targetSelf
                && selfMarker != null
                && isUsable(selfMarker.getPoint())) {
            target = selfMarker.getPoint();
        } else if (targetMarker != null
                && isUsable(targetMarker.getPoint())) {
            target = targetMarker.getPoint();
        } else {
            target = d.targetPoint().get();
        }

        renderGeometry(d, origin, target);
    }

    private void renderGeometry(
            MrsDrawing d,
            GeoPoint own,
            GeoPoint target) {

        if (d == null || !isUsable(own) || !isUsable(target)) {
            return;
        }

        double targetDistance = own.distanceTo(target);
        if (Double.isNaN(targetDistance) || targetDistance < 1.0) {
            return;
        }

        renderDrawing = d;
        renderDrawingId = d.id;
        renderHighlighted =
                (activeDrawingId != null
                        && activeDrawingId.equals(d.id))
                        || (highlightedDrawingId != null
                        && highlightedDrawingId.equals(d.id));

        double selectedTrueBearing = normalizeDegrees(own.bearingTo(target));
        double gridBearing = toGridBearing(
                own,
                target,
                selectedTrueBearing
        );
        int gridMil = MrsCoreLogic.snapMilToStep(
                degreesToMil(gridBearing),
                50
        );
        double trueBearing = gridMilToTrueBearing(
                own,
                target,
                gridMil
        );
        // Keep the visible arrow, range geometry and target marker on the
        // snapped 50-mil axis while preserving the selected range.
        target = GeoCalculations.pointAtDistance(
                own,
                trueBearing,
                targetDistance
        );
        StaticGeometry staticGeometry = getStaticGeometry(
                d.id,
                own,
                trueBearing
        );
        double bracketAnchor = targetDistance / 2.0;
        double visualResolution = getVisualResolution();
        lastVisualResolution = visualResolution;
        if (d.showFill) {
            addSectorFill(staticGeometry.fill);
        }
        addSectorBoundary(staticGeometry.leftBoundary);
        addSectorBoundary(staticGeometry.rightBoundary);

        int ringIndex = 0;
        for (double range = RANGE_STEP_M;
             range <= MAX_RANGE_M + 0.1;
             range += RANGE_STEP_M) {

            boolean fullKm = (((int) Math.round(range)) % 1000) == 0;
            boolean visible = fullKm ? d.showKm : d.showHalfKm;
            if (visible) {
                addRangeArc(
                        staticGeometry.arcs.get(ringIndex),
                        fullKm
                );
                addRangeTick(own, trueBearing, range, fullKm);
                if (d.showRangeLabels) {
                    addRangeLabelSmart(
                            own,
                            trueBearing,
                            range,
                            ringIndex,
                            bracketAnchor
                    );
                }
            }
            ringIndex++;
        }

        addCenterLine(own, target);
        addInteractionHitBox(own, target);
        addArrowHead(target, trueBearing);

        if (d.showTargetMarker) {
            addTargetMarker(target, trueBearing);
        }

        if (d.showBracket) {
            addCenterBracket(own, trueBearing, bracketAnchor);
            String bearingLabel = String.format(
                    Locale.GERMANY,
                    "%s  GR %04d mils",
                    d.label == null || d.label.trim().isEmpty()
                            ? "Mrs"
                            : d.label.trim(),
                    gridMil
            );
            String distanceLabel = formatTargetDistance(targetDistance);

            // Put both values beside the target arrow. Show each one as soon
            // as its own measured text width fits in the clear part of the
            // shaft between the center bracket and the arrow head.
            double bracketClearance = clamp(
                    visualResolution * 12.0,
                    50.0,
                    520.0
            );
            double arrowClearance = getArrowLegMeters() + clamp(
                    visualResolution * 10.0,
                    45.0,
                    420.0
            );
            double labelStartLimit = bracketAnchor
                    + getCenterBracketHalfWidth(bracketAnchor)
                    + bracketClearance;
            double labelEnd = targetDistance - arrowClearance;
            double availableLength = Math.max(0.0, labelEnd - labelStartLimit);
            double bearingLabelWidth = measureLabelWidthPixels(
                    bearingLabel,
                    16.0f
            );
            double distanceLabelWidth = measureLabelWidthPixels(
                    distanceLabel,
                    16.0f
            );
            double bearingLabelLength = MrsCoreLogic.annotationLengthMeters(
                    bearingLabelWidth,
                    16.0,
                    visualResolution
            );
            double distanceLabelLength = MrsCoreLogic.annotationLengthMeters(
                    distanceLabelWidth,
                    16.0,
                    visualResolution
            );
            double labelOffset = clamp(
                    visualResolution * 22.0,
                    55.0,
                    300.0
            );

            if (MrsCoreLogic.annotationFits(
                    availableLength,
                    bearingLabelWidth,
                    16.0,
                    visualResolution
            )) {
                addBracketLabel(
                        own,
                        trueBearing,
                        labelEnd - bearingLabelLength,
                        bearingLabelLength,
                        labelOffset,
                        bearingLabel
                );
            }
            if (MrsCoreLogic.annotationFits(
                    availableLength,
                    distanceLabelWidth,
                    16.0,
                    visualResolution
            )) {
                addBracketLabel(
                        own,
                        trueBearing,
                        labelEnd - distanceLabelLength,
                        distanceLabelLength,
                        -labelOffset,
                        distanceLabel
                );
            }
        }

        renderDrawing = null;
        renderDrawingId = null;
        renderHighlighted = false;
    }

    private StaticGeometry getStaticGeometry(
            String drawingId,
            GeoPoint own,
            double bearing) {
        String key = drawingId == null ? "__none__" : drawingId;
        StaticGeometry cached = geometryCache.get(key);
        if (cached != null && cached.matches(own, bearing)) {
            geometryCacheHits++;
            return cached;
        }

        geometryCacheMisses++;
        StaticGeometry created = new StaticGeometry(own, bearing);
        geometryCache.put(key, created);
        return created;
    }

    private void addSectorFill(List<GeoPoint> pts) {
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
        int color = renderDrawing == null
                ? sectorFillColor
                : renderDrawing.fillColor;
        int alpha = renderDrawing == null
                ? Color.alpha(color)
                : (int) clamp(renderDrawing.fillAlpha, 0.0, 255.0);
        sector.setFillColor(Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        ));
        addLocalItem(sector);
    }

    private void addSectorBoundary(List<GeoPoint> pts) {
        addLocalItem(makePolyline(
                pts,
                Color.WHITE,
                renderHighlighted ? 3.1 : 2.3,
                Shape.BASIC_LINE_STYLE_SOLID
        ));
    }

    private void addRangeArc(
            List<GeoPoint> pts,
            boolean fullKm) {

        Polyline arc = makePolyline(
                pts,
                fullKm ? COLOR_PRIMARY : COLOR_PRIMARY_SOFT,
                fullKm
                        ? (renderHighlighted ? 2.8 : 2.3)
                        : (renderHighlighted ? 1.45 : 1.15),
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

        double resolution = getVisualResolution();
        double halfWidth = fullKm
                ? clamp(resolution * 10.0, 28.0, 150.0)
                : clamp(resolution * 7.0, 20.0, 110.0);

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

    private void addRangeLabelSmart(
            GeoPoint own,
            double bearing,
            double range,
            int ringIndex,
            double bracketAnchor) {

        double resolution = getVisualResolution();
        double side = ringIndex % 2 == 0 ? 1.0 : -1.0;
        double cross = side
                * clamp(resolution * 9.0, 0.0, 85.0);

        // Keep ring labels clear of the central bracket/annotation cluster.
        // The clearance follows the current meters-per-pixel resolution so it
        // remains visually stable while zooming.
        double bracketClearance = clamp(
                resolution * 95.0,
                140.0,
                520.0
        );
        if (Math.abs(range - bracketAnchor) < bracketClearance) {
            cross = side * clamp(
                    resolution * 34.0,
                    70.0,
                    260.0
            );
        }
        double start = Math.max(
                40.0,
                range - clamp(resolution * 125.0, 150.0, 320.0)
        );
        double end = Math.max(
                80.0,
                range - clamp(resolution * 24.0, 35.0, 90.0)
        );

        List<GeoPoint> pts = new ArrayList<>();
        pts.add(pointFromAxis(own, bearing, start, cross));
        pts.add(pointFromAxis(own, bearing, end, cross));

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
                renderHighlighted ? 3.4 : 2.0,
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
                renderHighlighted ? 3.0 : 2.0,
                Shape.BASIC_LINE_STYLE_SOLID
        ));
        addLocalItem(makePolyline(
                right,
                Color.WHITE,
                renderHighlighted ? 3.0 : 2.0,
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
                renderHighlighted ? 3.4 : 2.7,
                Shape.BASIC_LINE_STYLE_SOLID
        ));
        addLocalItem(makePolyline(
                cross2,
                COLOR_TARGET,
                renderHighlighted ? 3.4 : 2.7,
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
        double halfWidth = getCenterBracketHalfWidth(anchor);
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
                renderHighlighted ? 4.1 : 3.0,
                Shape.BASIC_LINE_STYLE_SOLID
        ));
        addLocalItem(makePolyline(
                lower,
                Color.WHITE,
                renderHighlighted ? 4.1 : 3.0,
                Shape.BASIC_LINE_STYLE_SOLID
        ));
    }

    private double getCenterBracketHalfWidth(double anchor) {
        double arrowLeg = getArrowLegMeters();
        double maxHalfWidth = Math.max(
                20.0,
                Math.min(650.0, anchor * 0.45)
        );
        return Math.min(
                clamp(arrowLeg * 1.40, 28.0, 650.0),
                maxHalfWidth
        );
    }

    /**
     * Draw one annotation parallel to the centerline and offset from it. The
     * transparent carrier line keeps the text aligned to the selected bearing
     * even when the map is rotated.
     */
    private void addBracketLabel(
            GeoPoint own,
            double bearing,
            double start,
            double labelLength,
            double crossOffset,
            String text) {

        List<GeoPoint> pts = new ArrayList<>();
        pts.add(pointFromAxis(
                own,
                bearing,
                start,
                crossOffset
        ));
        pts.add(pointFromAxis(
                own,
                bearing,
                start + labelLength,
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

    private static double measureLabelWidthPixels(
            String text,
            float textSize) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        return paint.measureText(text);
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
        if (renderDrawingId != null) {
            item.setMetaString(META_MRS_DRAWING_ID, renderDrawingId);
        }
        item.setClickable(true);
        item.setEditable(false);
        item.setMovable(false);
        renderGroup.addItem(item);
        overlayItems.add(item);
    }

    private void clearOverlayItems() {
        MapEventDispatcher dispatcher = mapView.getMapEventDispatcher();
        for (MapItem item : new ArrayList<>(overlayItems)) {
            dispatcher.removeMapItemEventListener(item, this);
        }
        // Clear the dedicated render group as a whole: ATAK may retain child
        // map items beyond their Java-side references after rapid redraws.
        renderGroup.clearItems();
        overlayItems.clear();
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

    private static double gridMilToTrueBearing(
            GeoPoint own,
            GeoPoint target,
            int gridMil) {
        double convergence = ATAKUtilities.computeGridConvergence(own, target);
        if (Double.isNaN(convergence)) {
            convergence = 0.0;
        }
        return normalizeDegrees(
                MrsCoreLogic.milToDegrees(gridMil) + convergence
        );
    }

    private static int degreesToMil(double degrees) {
        return MrsCoreLogic.degreesToMil(degrees);
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

    private String formatOriginCoordinate() {
        if (originPoint == null || !isUsable(originPoint.get())) {
            return "Nicht verfügbar";
        }

        return CoordinateFormatUtilities.formatToString(
                originPoint.get(),
                CoordinateFormat.MGRS
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

    /**
     * A point is usable if it is finite and inside the valid coordinate range.
     * This is the same range MrsDrawing.fromJson accepts, so every saved
     * drawing can be loaded again.
     */
    private static boolean isUsable(GeoPoint point) {
        if (point == null) {
            return false;
        }
        double lat = point.getLatitude();
        double lon = point.getLongitude();
        return !Double.isNaN(lat)
                && !Double.isNaN(lon)
                && !Double.isInfinite(lat)
                && !Double.isInfinite(lon)
                && Math.abs(lat) <= 90.0
                && Math.abs(lon) <= 180.0;
    }

    private static double normalizeDegrees(double value) {
        return MrsCoreLogic.normalizeDegrees(value);
    }
}
