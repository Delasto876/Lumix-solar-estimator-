package com.lumix.estimator.site.map

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * "REPLACE THE CURRENT MAP IMPLEMENTATION... MAPLIBRE GL JS" (2026-08-18): GL JS is a browser/
 * WebGL library and does not apply to this native Kotlin/Compose app — this wraps MapLibre
 * Native's classic Android `MapView` (a `FrameLayout`) via `AndroidView`, the same interop
 * pattern `com.google.maps.android.compose.GoogleMap` (what this replaces) used internally. The
 * style-JSON format is identical between MapLibre Native and MapLibre GL JS, so
 * [OpenFreeMapProvider][com.lumix.estimator.map.OpenFreeMapProvider]'s style URL is unchanged
 * either way.
 *
 * **Verification note**: this file — and this file alone — could not be compiled or run in this
 * development environment (no working Android build in this sandbox; see the module README's own
 * disclosure). The `MapView`/`MapLibreMap`/`Style` API surface below is written from the
 * MapLibre Native Android SDK's documented public API, but has NOT been confirmed against the
 * exact `org.maplibre.gl:android-sdk` version pinned in `build.gradle.kts`. Run `./gradlew
 * assembleDebug` first when picking this up — if a method name doesn't resolve, this is the one
 * file to check; every other file in this change only depends on [MapLibreMapView]'s own small,
 * already-Kotlin-idiomatic callback surface below, not on MapLibre's classes directly.
 *
 * Deliberately does NOT use MapLibre's classic `addMarker`/`addPolygon` annotations API (whose
 * exact availability varies more across SDK major versions) — the map screen instead renders
 * every marker/polygon/vertex-handle through `GeoJsonSource` + style layers (`FillLayer`/
 * `LineLayer`/`CircleLayer`), the foundational styling API that has been stable since this
 * engine's original Mapbox GL Native lineage and ships in the core `android-sdk` artifact with no
 * optional plugin dependency.
 */
@Composable
fun MapLibreMapView(
    styleUrl: String,
    modifier: Modifier = Modifier,
    onMapReady: (MapLibreMap, Style) -> Unit,
    /**
     * 2026-08-19 map diagnostics (Part 1): carries MapLibre's own [errorMessage] through instead of
     * discarding it — `OnDidFailLoadingMapListener.onDidFailLoadingMap(String)` is the stable
     * signature this SDK lineage has used since its original Mapbox GL Native days. A 401/403 from
     * a MapTiler key/User-Agent mismatch surfaces here as MapLibre's own HTTP-failure text; this is
     * always logged (`LumixMapDiag`, below) even when the caller doesn't pass a callback, so the
     * failure reason is never silently swallowed.
     */
    onStyleLoadFailed: ((errorMessage: String) -> Unit)? = null
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { mutableMapViewHolder() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val view = mapView.value ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_CREATE -> view.onCreate(null)
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                Lifecycle.Event.ON_DESTROY -> view.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val view = MapView(context)
            mapView.value = view
            view.onCreate(null)
            view.onStart()
            view.onResume()
            view.getMapAsync { map ->
                Log.d("LumixMapDiag", "requesting style: $styleUrl")
                map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                    Log.d("LumixMapDiag", "style loaded OK: $styleUrl")
                    onMapReady(map, style)
                }
            }
            // This listener is registered once on the persistent MapView, so it also fires for
            // later style switches driven directly against the MapLibreMap (see
            // SolarSiteMapScreen.switchMapStyle) — not just this composable's own initial style
            // load. `styleUrl` above is only ever the initial value (this factory block doesn't
            // re-run on recomposition — see the `update` block's own comment), so it's deliberately
            // NOT repeated here to avoid a misleadingly stale URL on a later switch's failure; the
            // caller logs which URL it just requested immediately beforehand instead.
            view.addOnDidFailLoadingMapListener { errorMessage ->
                Log.e("LumixMapDiag", "style FAILED to load: $errorMessage")
                onStyleLoadFailed?.invoke(errorMessage)
            }
            view
        },
        update = { /* style/camera changes are driven imperatively via the MapLibreMap the caller received in onMapReady, not by re-running this factory */ }
    )
}

/** A tiny holder so the [DisposableEffect] above can reach the [MapView] the [AndroidView] factory creates, without recomposing this composable's own state. */
private class MapViewHolder {
    var value: MapView? = null
}

private fun mutableMapViewHolder() = MapViewHolder()

/** Blocking, one-time MapLibre runtime init — must happen before any [MapView] is created. Safe to call more than once (MapLibre's own instance getter is idempotent). */
fun ensureMapLibreInitialized(context: Context) {
    org.maplibre.android.MapLibre.getInstance(context)
}
