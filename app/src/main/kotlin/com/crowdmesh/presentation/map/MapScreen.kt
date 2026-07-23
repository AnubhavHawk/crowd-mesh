package com.crowdmesh.presentation.map

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val OFFLINE_STYLE_ASSET_URI = "asset://map/offline_style.json"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val heatmapCells by viewModel.heatmapCells.collectAsState()

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

@Composable
private fun MapLibreHeatmapView(
    heatmapCells: List<com.crowdmesh.domain.model.HeatmapCell>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    val latestCells = rememberUpdatedState(heatmapCells)

    // MapLibre.getInstance() is also called eagerly in CrowdMeshApp.onCreate(), which
    // is the actual guarantee; this call is just a same-thread, idempotent safety net
    // that runs synchronously *before* the MapView below is constructed (a LaunchedEffect
    // here would not be soon enough — its coroutine can run after this composable body,
    // by which point MapView(context) would already have thrown).
    MapLibre.getInstance(context)

    val mapView = remember {
        MapView(context).apply {
            getMapAsync { map ->
                map.setStyle(Style.Builder().fromUri(OFFLINE_STYLE_ASSET_URI)) { style ->
                    style.addSource(GeoJsonSource(HeatmapLayerBuilder.SOURCE_ID, HeatmapLayerBuilder.buildGeoJson(latestCells.value)))
                    style.addLayer(buildFillLayer())
                    loadedStyle = style
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val bundle = Bundle()
        mapView.onCreate(bundle)
        val observer = LifecycleEventObserver { _, event ->
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
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(heatmapCells, loadedStyle) {
        val source = loadedStyle?.getSourceAs<GeoJsonSource>(HeatmapLayerBuilder.SOURCE_ID)
        source?.setGeoJson(HeatmapLayerBuilder.buildGeoJson(heatmapCells))
    }

    AndroidView(factory = { mapView }, modifier = modifier)
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
    )

@Composable
private fun HeatmapLegend(modifier: Modifier = Modifier) {
    // Intentionally minimal: a text legend is enough for a prototype; a full
    // gradient swatch could be added with a Canvas draw if desired later.
    Box(modifier) {
        Text(
            text = "${stringResource(R.string.map_legend_low)} -> ${stringResource(R.string.map_legend_high)}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier,
        )
    }
}
