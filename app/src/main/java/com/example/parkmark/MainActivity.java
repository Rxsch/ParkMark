package com.example.parkmark;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MainActivity extends AppCompatActivity {

    private int currentTab = R.id.navHome;
    private String parkingNotes = "";

    private static final int LOCATION_REQUEST_CODE = 101;

    private MapView mapView;
    private GoogleMap googleMap;

    private FusedLocationProviderClient locationClient;
    private SharedPreferences parkingPreferences;

    private double savedLatitude = Double.NaN;
    private double savedLongitude = Double.NaN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        locationClient =
                LocationServices.getFusedLocationProviderClient(this);

        parkingPreferences =
                getSharedPreferences("ParkMarkParking", MODE_PRIVATE);

        savedLatitude = Double.longBitsToDouble(
                parkingPreferences.getLong(
                        "latitude",
                        Double.doubleToRawLongBits(Double.NaN)
                )
        );

        savedLongitude = Double.longBitsToDouble(
                parkingPreferences.getLong(
                        "longitude",
                        Double.doubleToRawLongBits(Double.NaN)
                )
        );

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        if (savedInstanceState != null) {
            currentTab = savedInstanceState.getInt(
                    "currentTab", R.id.navHome
            );

            parkingNotes = savedInstanceState.getString(
                    "parkingNotes", ""
            );
        }

        showPage(currentTab);
    }

    private void showPage(int tabId) {
        if (mapView != null) {
            mapView.onPause();
            mapView.onStop();
            mapView.onDestroy();
            mapView = null;
            googleMap = null;
        }

        currentTab = tabId;

        if (tabId == R.id.navParking) {
            setContentView(R.layout.activity_parking);

            EditText notesInput = findViewById(R.id.notesInput);
            notesInput.setText(parkingNotes);

            findViewById(R.id.updateLocationButton)
                    .setOnClickListener(view -> saveParkingLocation());

        } else if (tabId == R.id.navTimer) {
            setContentView(R.layout.activity_alerts);

        } else if (tabId == R.id.navMap) {
            setContentView(R.layout.activity_map);

            setupGoogleMap();

            findViewById(R.id.navigateButton).setOnClickListener(view -> navigateToCar());

        } else {
            currentTab = R.id.navHome;
            setContentView(R.layout.activity_main);

            findViewById(R.id.openMapButton)
                    .setOnClickListener(view -> showPage(R.id.navMap));

            findViewById(R.id.parkButton)
                    .setOnClickListener(view -> saveParkingLocation());
        }

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main), (view, insets) -> {
                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    view.setPadding(
                            bars.left,
                            bars.top,
                            bars.right,
                            bars.bottom
                    );

                    return insets;
                }
        );

        BottomNavigationView navigation =
                findViewById(R.id.bottomNavigation);

        navigation.setItemActiveIndicatorEnabled(false);
        navigation.setSelectedItemId(currentTab);

        navigation.setOnItemSelectedListener(item -> {
            int selectedTab = item.getItemId();

            if (selectedTab != R.id.navHome
                    && selectedTab != R.id.navParking
                    && selectedTab != R.id.navTimer
                    && selectedTab != R.id.navMap){
                return false;
            }

            if (selectedTab != currentTab) {
                rememberNotes();
                showPage(selectedTab);
            }

            return true;
        });
    }

    private void rememberNotes() {
        if (currentTab == R.id.navParking) {
            EditText notesInput = findViewById(R.id.notesInput);
            parkingNotes = notesInput.getText().toString();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        rememberNotes();

        outState.putInt("currentTab", currentTab);
        outState.putString("parkingNotes", parkingNotes);

        super.onSaveInstanceState(outState);
    }
    private void setupGoogleMap() {

        mapView = findViewById(R.id.googleMapView);

        mapView.onCreate(null);
        mapView.onStart();
        mapView.onResume();

        mapView.getMapAsync(map -> {

            googleMap = map;

            googleMap.getUiSettings()
                    .setZoomControlsEnabled(true);

            googleMap.getUiSettings()
                    .setCompassEnabled(true);

            LatLng ucf = new LatLng(28.6024, -81.2001);

            googleMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(ucf, 15f)
            );

            showSavedParkingMarker();

            if (hasLocationPermission()) {
                enableMyLocation();
            }
        });
    }


    private void showSavedParkingMarker() {

        if (googleMap == null) {
            return;
        }

        if (Double.isNaN(savedLatitude)
                || Double.isNaN(savedLongitude)) {
            return;
        }

        LatLng parkingLocation =
                new LatLng(savedLatitude, savedLongitude);

        googleMap.clear();

        googleMap.addMarker(
                new MarkerOptions()
                        .position(parkingLocation)
                        .title("My Parked Car")
                        .snippet("Saved parking location")
                        .icon(
                                BitmapDescriptorFactory
                                        .defaultMarker(
                                                BitmapDescriptorFactory.HUE_ORANGE
                                        )
                        )
        );

        googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                        parkingLocation,
                        17f
                )
        );
    }


    private boolean hasLocationPermission() {

        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

                ||

                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED;
    }

    private void enableMyLocation() {

        if (googleMap == null || !hasLocationPermission()) {
            return;
        }

        try {
            googleMap.setMyLocationEnabled(true);

            googleMap.getUiSettings()
                    .setMyLocationButtonEnabled(true);

        } catch (SecurityException e) {
            Toast.makeText(
                    this,
                    "Location permission required",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    private void saveParkingLocation() {

        if (!hasLocationPermission()) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_REQUEST_CODE
            );

            return;
        }

        try {

            locationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
            ).addOnSuccessListener(location -> {

                if (location == null) {

                    Toast.makeText(
                            this,
                            "Could not get location. Check GPS and try again.",
                            Toast.LENGTH_LONG
                    ).show();

                    return;
                }

                savedLatitude = location.getLatitude();
                savedLongitude = location.getLongitude();

                parkingPreferences.edit()
                        .putLong(
                                "latitude",
                                Double.doubleToRawLongBits(savedLatitude)
                        )
                        .putLong(
                                "longitude",
                                Double.doubleToRawLongBits(savedLongitude)
                        )
                        .apply();

                Toast.makeText(
                        this,
                        "Parking location saved!",
                        Toast.LENGTH_SHORT
                ).show();

                rememberNotes();
                showPage(R.id.navMap);

            }).addOnFailureListener(error -> {

                Toast.makeText(
                        this,
                        "GPS error: " + error.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            });

        } catch (SecurityException e) {

            Toast.makeText(
                    this,
                    "Location permission required",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    private void navigateToCar() {

        if (Double.isNaN(savedLatitude)
                || Double.isNaN(savedLongitude)) {

            Toast.makeText(
                    this,
                    "Save your parking location first",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Uri uri = Uri.parse(
                "https://www.google.com/maps/dir/?api=1"
                        + "&destination="
                        + savedLatitude
                        + ","
                        + savedLongitude
                        + "&travelmode=walking"
        );

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);

        startActivity(intent);
    }


    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == LOCATION_REQUEST_CODE) {

            if (hasLocationPermission()) {

                enableMyLocation();
                saveParkingLocation();

            } else {

                Toast.makeText(
                        this,
                        "Location permission was denied",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }


    @Override
    protected void onStart() {
        super.onStart();

        if (mapView != null) {
            mapView.onStart();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() {

        if (mapView != null) {
            mapView.onPause();
        }

        super.onPause();
    }

    @Override
    protected void onStop() {

        if (mapView != null) {
            mapView.onStop();
        }

        super.onStop();
    }

    @Override
    protected void onDestroy() {

        if (mapView != null) {
            mapView.onDestroy();
            mapView = null;
        }

        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        if (mapView != null) {
            mapView.onLowMemory();
        }
    }
}