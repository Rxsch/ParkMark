package com.example.parkmark.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.example.parkmark.data.ParkingLocation;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

// Keep one MapView lifecycle tied to the activity, even when bottom-navigation replaces a page.
public final class ParkingMap {
    private final AppCompatActivity activity;
    private MapView view;
    private GoogleMap map;
    private boolean started;
    private boolean resumed;
    private ParkingLocation location;
    private long shownId = -1;
    private double shownLatitude = Double.NaN;
    private double shownLongitude = Double.NaN;

    public ParkingMap(AppCompatActivity activity) { this.activity = activity; }

    public void attach(FrameLayout container, ParkingLocation location) {
        this.location = location;
        if (!configured()) {
            TextView message = new TextView(activity);
            message.setGravity(Gravity.CENTER);
            message.setPadding(32, 32, 32, 120);
            message.setText("Map preview is unavailable. You can still save parking and use Navigate to Car.");
            container.addView(message, new FrameLayout.LayoutParams(-1, -1));
            return;
        }
        view = new MapView(activity);
        container.addView(view, new FrameLayout.LayoutParams(-1, -1));
        view.onCreate(null);
        if (activity.getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) start();
        if (activity.getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) resume();
        MapView attached = view;
        view.getMapAsync(ready -> {
            if (view != attached) return;
            map = ready;
            map.getUiSettings().setZoomControlsEnabled(true);
            map.getUiSettings().setMapToolbarEnabled(false);
            update(this.location);
        });
    }

    // An absent API key leaves navigation available instead of crashing when the map opens.
    private boolean configured() {
        try {
            Bundle metadata = activity.getPackageManager().getApplicationInfo(
                    activity.getPackageName(), PackageManager.GET_META_DATA).metaData;
            return metadata != null && !metadata.getString("com.google.android.geo.API_KEY", "").trim().isEmpty();
        } catch (PackageManager.NameNotFoundException error) { return false; }
    }

    public void update(ParkingLocation current) {
        location = current;
        if (map == null) return;
        boolean hasPermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        try { map.setMyLocationEnabled(hasPermission); } catch (SecurityException ignored) { }
        if (current == null) {
            map.clear();
            shownId = -1;
            return;
        }
        LatLng target = new LatLng(current.latitude, current.longitude);
        map.clear();
        com.google.android.gms.maps.model.Marker marker = map.addMarker(new MarkerOptions()
                .position(target).title("Your parked car").snippet(current.address));
        if (marker != null) marker.showInfoWindow();
        if (shownId != current.id || shownLatitude != current.latitude || shownLongitude != current.longitude)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 17f));
        shownId = current.id; shownLatitude = current.latitude; shownLongitude = current.longitude;
    }

    // Walking directions use the saved destination and need no location permission from ParkMark.
    public static void navigate(AppCompatActivity activity, ParkingLocation location) {
        if (location == null) {
            Toast.makeText(activity, "Save a parking location first.", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
                .appendQueryParameter("api", "1")
                .appendQueryParameter("destination", location.latitude + "," + location.longitude)
                .appendQueryParameter("travelmode", "walking").build();
        Intent intent = new Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps");
        try { activity.startActivity(intent); }
        catch (ActivityNotFoundException missingMaps) {
            try { activity.startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
            catch (ActivityNotFoundException missingBrowser) {
                Toast.makeText(activity, "Install Google Maps or a browser to open directions.", Toast.LENGTH_LONG).show();
            }
        }
    }

    public void start() { if (view != null && !started) { view.onStart(); started = true; } }
    public void resume() { if (view != null && !resumed) { view.onResume(); resumed = true; } }
    public void pause() { if (view != null && resumed) { view.onPause(); resumed = false; } }
    public void stop() { pause(); if (view != null && started) { view.onStop(); started = false; } }
    public void destroy() { stop(); if (view != null) view.onDestroy(); view = null; map = null; shownId = -1; }
    public void lowMemory() { if (view != null) view.onLowMemory(); }
}
