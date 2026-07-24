package com.crowdmesh.presentation.map

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.crowdmesh.R
import com.crowdmesh.domain.model.DensityLevel
import com.crowdmesh.domain.model.PresenceRecord
import com.crowdmesh.presentation.common.DensityGreen
import com.crowdmesh.presentation.common.DensityOrange
import com.crowdmesh.presentation.common.DensityRed
import com.crowdmesh.presentation.common.DensityYellow
import com.crowdmesh.util.Logger
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.concurrent.atomic.AtomicInteger
import com.crowdmesh.domain.geohash.GeohashEncoder

private const val TAG = "MapScreen"

private const val OFFLINE_STYLE_ASSET_URI = "asset://map/offline_style.json"

// A precision-6 geohash cell is ~0.6km x 1.2km at the equator — zoom 15 keeps it
// comfortably on-screen instead of the sub-pixel dot it is at world zoom.
private const val FOCUSED_ZOOM = 15.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    // TEMPORARY raw diagnostic — bypasses our own Logger wrapper entirely, to isolate
    // whether Logger itself is broken vs. this composable never actually running.
    android.util.Log.e("MAPDEBUG_RAW", "MapScreen composable entered")

    val heatmapCells by viewModel.heatmapCells.collectAsState()
    val ownRecord by viewModel.ownRecord.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.map_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            MapLibreHeatmapView(
                heatmapCells = heatmapCells,
                ownRecord = ownRecord,
                modifier = Modifier.fillMaxSize(),
            )
            HeatmapLegend(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            )
        }
    }
}

// TEMPORARY: counts how many times MapLibreHeatmapView's composable body runs, to catch
// unexpected recomposition/recreation. Not per-instance (module-level), fine for debugging
// a single-map-screen-at-a-time app; remove alongside the rest of the [MAP] logging.
private val mapComposeCount = AtomicInteger(0)

@Composable
private fun MapLibreHeatmapView(
    heatmapCells: List<com.crowdmesh.domain.model.HeatmapCell>,
    ownRecord: PresenceRecord?,
    modifier: Modifier = Modifier,
) {
    val composeCall = mapComposeCount.incrementAndGet()
    android.util.Log.e("MAPDEBUG_RAW", "MapLibreHeatmapView composing (call #$composeCall)")
    Logger.w(TAG, "[MAP] MapLibreHeatmapView composing (call #$composeCall), heatmapCells.size=${heatmapCells.size}")

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var hasCenteredOnData by remember { mutableStateOf(false) }
    val latestCells = rememberUpdatedState(heatmapCells)
    val latestOwnRecord = rememberUpdatedState(ownRecord)

    // MapLibre.getInstance() is also called eagerly in CrowdMeshApp.onCreate(), which
    // is the actual guarantee; this call is just a same-thread, idempotent safety net
    // that runs synchronously *before* the MapView below is constructed (a LaunchedEffect
    // here would not be soon enough — its coroutine can run after this composable body,
    // by which point MapView(context) would already have thrown).
    MapLibre.getInstance(context)
    Logger.w(TAG, "[MAP] MapLibre.getInstance() returned")

    val mapView = remember {
        android.util.Log.e("MAPDEBUG_RAW", "remember{} block entered")
        Logger.w(TAG, "[MAP] remember{} block running — constructing MapView (this should happen exactly once per screen visit)")
        val view = MapView(context)
        Logger.w(TAG, "[MAP] MapView constructed")

        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Logger.w(TAG, "[MAP] MapView.onViewAttachedToWindow")
            override fun onViewDetachedFromWindow(v: View) = Logger.w(TAG, "[MAP] MapView.onViewDetachedFromWindow")
        })

        // MapView-level listeners fire independent of our own getMapAsync/setStyle callback
        // chain below — if these fire but our chain's logs don't (or vice versa), that
        // pinpoints exactly which side of the callback the failure is on.
        view.addOnWillStartLoadingMapListener { Logger.w(TAG, "[MAP] MapView.onWillStartLoadingMap") }
        view.addOnDidFinishLoadingMapListener { Logger.w(TAG, "[MAP] MapView.onDidFinishLoadingMap") }
        view.addOnDidFailLoadingMapListener { error -> Logger.w(TAG, "[MAP] MapView.onDidFailLoadingMap: $error") }
        view.addOnDidFinishLoadingStyleListener { Logger.w(TAG, "[MAP] MapView.onDidFinishLoadingStyle (MapView-level listener)") }
        view.addOnDidBecomeIdleListener { Logger.w(TAG, "[MAP] MapView.onDidBecomeIdle (renderer fully idle = first frame done)") }

        Logger.w(TAG, "[MAP] calling getMapAsync()")
        view.getMapAsync { map ->
            Logger.w(TAG, "[MAP] getMapAsync callback invoked — MapLibreMap instance ready")
            mapLibreMap = map
            Logger.w(TAG, "[MAP] calling map.setStyle(uri=$OFFLINE_STYLE_ASSET_URI)")
            map.setStyle(Style.Builder().fromUri(OFFLINE_STYLE_ASSET_URI)) { style ->
                Logger.w(TAG, "[MAP] style-loaded callback invoked")
                style.addSource(GeoJsonSource(HeatmapLayerBuilder.SOURCE_ID, HeatmapLayerBuilder.buildGeoJson(latestCells.value)))
                Logger.w(TAG, "[MAP] heatmap GeoJSON source added")
                style.addLayer(buildFillLayer())
                Logger.w(TAG, "[MAP] fill layer added")
                style.addSource(
                    GeoJsonSource(
                        HeatmapLayerBuilder.OWN_LOCATION_SOURCE_ID,
                        HeatmapLayerBuilder.buildOwnLocationGeoJson(latestOwnRecord.value),
                    ),
                )
                style.addLayer(buildOwnLocationLayer())
                Logger.w(TAG, "[MAP] own-location source and layer added")
                // Fallback viewport for the case where no data has arrived yet — this is
                // NOT where the camera stays once real cells exist, see the
                // hasCenteredOnData block below, which is the actual fix for the "map is
                // visually empty" bug: this fixed (0,0)/zoom-1 view was previously the
                // *only* camera placement ever applied, so real-world cells (which are
                // sub-pixel-sized at zoom 1 and never at 0,0 anyway) were never visible.
                map.cameraPosition = CameraPosition.Builder().target(LatLng(0.0, 0.0)).zoom(1.0).build()
                Logger.w(TAG, "[MAP] camera position set (fallback, world view)")
                loadedStyle = style
                Logger.w(TAG, "[MAP] loadedStyle state updated — MapLibreHeatmapView should recompose")
            }
        }
        view
    }

    DisposableEffect(lifecycleOwner, mapView) {
        Logger.w(TAG, "[MAP] DisposableEffect launched, calling mapView.onCreate()")
        val bundle = Bundle()
        mapView.onCreate(bundle)
        Logger.w(TAG, "[MAP] mapView.onCreate() returned, current lifecycle state=${lifecycleOwner.lifecycle.currentState}")
        val observer = LifecycleEventObserver { _, event ->
            Logger.w(TAG, "[MAP] lifecycle event received: $event")
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            Logger.w(TAG, "[MAP] DisposableEffect disposing, calling mapView.onDestroy()")
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(heatmapCells, loadedStyle) {
        val source = loadedStyle?.getSourceAs<GeoJsonSource>(HeatmapLayerBuilder.SOURCE_ID)

        heatmapCells.forEach { cell ->
            val bounds = GeohashEncoder.decodeBounds(cell.geohash)
            Logger.d(
                TAG,
                "[PIPELINE] cell geohash=${cell.geohash} center=(${bounds.centerLat}, ${bounds.centerLon}) " +
                    "userCount=${cell.userCount} level=${cell.level} confidence=${cell.confidence} " +
                    "latestTimestampMillis=${cell.latestTimestampMillis}",
            )
        }

        val geoJson = HeatmapLayerBuilder.buildGeoJson(heatmapCells)
        Logger.d(
            TAG,
            "[PIPELINE] applying ${heatmapCells.size} heatmap cell(s) to map source " +
                "(styleLoaded=${loadedStyle != null}, sourceFound=${source != null}), " +
                "geoJson=$geoJson",
        )
        source?.setGeoJson(geoJson)

        // The camera previously never moved from the fixed (0,0)/zoom-1 fallback set at
        // style-load time (see above) — real cells were therefore always outside the
        // visible viewport (or, at that zoom, sub-pixel even when in view). Center on the
        // first real cell the first time data arrives, so mesh-synced peers are actually
        // visible instead of silently rendering an empty gray world map.
        if (!hasCenteredOnData && heatmapCells.isNotEmpty()) {
            val map = mapLibreMap
            val target = heatmapCells.first().geohash.let { GeohashEncoder.decodeBounds(it) }
            Logger.w(
                TAG,
                "[PIPELINE] first non-empty cell set received — centering camera on " +
                    "(${target.centerLat}, ${target.centerLon}), mapReady=${map != null}",
            )
            if (map != null) {
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(target.centerLat, target.centerLon))
                    .zoom(FOCUSED_ZOOM)
                    .build()
                hasCenteredOnData = true
            }
        }
    }

    LaunchedEffect(ownRecord, loadedStyle) {
        val ownSource = loadedStyle?.getSourceAs<GeoJsonSource>(HeatmapLayerBuilder.OWN_LOCATION_SOURCE_ID)
        Logger.d(
            TAG,
            "[PIPELINE] applying own location (userId=${ownRecord?.userId}, geohash=${ownRecord?.geohash}) " +
                "to map source (styleLoaded=${loadedStyle != null}, sourceFound=${ownSource != null})",
        )
        ownSource?.setGeoJson(HeatmapLayerBuilder.buildOwnLocationGeoJson(ownRecord))

        // Only auto-center on our own cell if the heatmap hasn't already centered on
        // real data (see the heatmapCells effect above) — own location takes priority
        // as the more meaningful "where am I" anchor once it's available.
        if (!hasCenteredOnData && ownRecord != null) {
            val map = mapLibreMap
            val bounds = GeohashEncoder.decodeBounds(ownRecord.geohash)
            if (map != null) {
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(bounds.centerLat, bounds.centerLon))
                    .zoom(FOCUSED_ZOOM)
                    .build()
                hasCenteredOnData = true
            }
        }
    }

    AndroidView(
        factory = {
            Logger.w(TAG, "[MAP] AndroidView factory{} invoked")
            mapView
        },
        update = { view ->
            Logger.w(TAG, "[MAP] AndroidView update() called, view size=${view.width}x${view.height}, isAttachedToWindow=${view.isAttachedToWindow}")
        },
        modifier = modifier,
    )
}

private fun buildFillLayer(): FillLayer =
    FillLayer(HeatmapLayerBuilder.FILL_LAYER_ID, HeatmapLayerBuilder.SOURCE_ID).withProperties(
        PropertyFactory.fillColor(
            Expression.match(
                Expression.get(HeatmapLayerBuilder.PROPERTY_LEVEL),
                Expression.color(android.graphics.Color.parseColor(HeatmapLayerBuilder.colorHexFor(DensityLevel.GREEN))),
                Expression.stop(
                    DensityLevel.YELLOW.name,
                    Expression.color(android.graphics.Color.parseColor(HeatmapLayerBuilder.colorHexFor(DensityLevel.YELLOW))),
                ),
                Expression.stop(
                    DensityLevel.ORANGE.name,
                    Expression.color(android.graphics.Color.parseColor(HeatmapLayerBuilder.colorHexFor(DensityLevel.ORANGE))),
                ),
                Expression.stop(
                    DensityLevel.RED.name,
                    Expression.color(android.graphics.Color.parseColor(HeatmapLayerBuilder.colorHexFor(DensityLevel.RED))),
                ),
            ),
        ),
        PropertyFactory.fillOpacity(
            Expression.interpolate(
                Expression.linear(),
                Expression.get(HeatmapLayerBuilder.PROPERTY_CONFIDENCE),
                Expression.stop(0f, Expression.literal(0.15f)),
                Expression.stop(1f, Expression.literal(0.65f)),
            ),
        ),
        // Without a basemap underneath, cell borders are the only thing that lets a user
        // tell where one geohash block ends and the next begins.
        PropertyFactory.fillOutlineColor(android.graphics.Color.parseColor("#80263238")),
    )

private fun buildOwnLocationLayer(): CircleLayer =
    CircleLayer(HeatmapLayerBuilder.OWN_LOCATION_LAYER_ID, HeatmapLayerBuilder.OWN_LOCATION_SOURCE_ID).withProperties(
        PropertyFactory.circleRadius(9f),
        PropertyFactory.circleColor(android.graphics.Color.parseColor("#2F6690")),
        PropertyFactory.circleStrokeWidth(3f),
        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
    )

@Composable
private fun HeatmapLegend(modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .padding(10.dp),
    ) {
        LegendRow(DensityGreen, stringResource(R.string.map_legend_green))
        LegendRow(DensityYellow, stringResource(R.string.map_legend_yellow))
        LegendRow(DensityOrange, stringResource(R.string.map_legend_orange))
        LegendRow(DensityRed, stringResource(R.string.map_legend_red))
    }
}

@Composable
private fun LegendRow(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 6.dp).width(130.dp),
        )
    }
}
