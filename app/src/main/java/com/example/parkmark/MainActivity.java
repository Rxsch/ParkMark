package com.example.parkmark;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.parkmark.data.*;
import com.example.parkmark.ui.FormDialog;
import com.example.parkmark.ui.AlertsController;
import com.example.parkmark.ui.ParkingMap;
import com.example.parkmark.util.PhotoStorage;
import com.example.parkmark.util.TimeText;
import com.google.android.gms.location.CurrentLocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final ExecutorService PHOTOS = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ParkRepository repository;
    private ParkingState state;
    private ParkingMap parkingMap;
    private AlertsController alerts;
    private int currentTab = R.id.navHome;
    private long editorLocationId = -1;
    private long photoLocationId = -1;
    private int photoIndex;
    private File cameraFile;
    private CancellationTokenSource locationRequest;
    private boolean newParking = true;
    private boolean locating;
    private Toast toast;

    // Register activity results before creation so camera and permission results survive recreation.
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) message("Reminders are saved, but Android notifications are off. Enable them in Reminder settings.");
                repository.refresh(error -> { if (error != null) message("Could not refresh reminders."); });
            });
    private final ActivityResultLauncher<String[]> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (hasLocationPermission()) locate();
                else message("Location permission was denied. You can enter coordinates from the menu.");
            });
    private final ActivityResultLauncher<String[]> photoPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) savePhoto(uri); });
    private final ActivityResultLauncher<Uri> camera = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraFile != null) savePhoto(Uri.fromFile(cameraFile));
                else if (cameraFile != null) cameraFile.delete();
            });

    // Observe database snapshots; all four pages read the same persistent parking state.
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = ParkRepository.get(this);
        parkingMap = new ParkingMap(this);
        alerts = new AlertsController(this, repository, this::message, this::requestNotifications);
        if (getIntent().getBooleanExtra("openAlerts", false)) currentTab = R.id.navTimer;
        if (savedInstanceState != null) {
            currentTab = savedInstanceState.getInt("tab", R.id.navHome);
            photoLocationId = savedInstanceState.getLong("photoLocation", -1);
            newParking = savedInstanceState.getBoolean("newParking", true);
            String pending = savedInstanceState.getString("cameraFile");
            if (pending != null) cameraFile = new File(pending);
        }
        showPage(currentTab);
        repository.state().observe(this, snapshot -> {
            state = snapshot;
            renderState();
        });
        repository.refresh(error -> { if (error != null) message("Could not load parking data. Please reopen the app."); });
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (currentTab != R.id.navHome) showPage(R.id.navHome);
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); setEnabled(true); }
            }
        });
    }

    // Preserve unsaved notes before switching screens and release each map exactly once.
    private void showPage(int tab) {
        persistDetails();
        parkingMap.destroy();
        currentTab = tab;
        editorLocationId = -1;
        int layout = tab == R.id.navParking ? R.layout.activity_parking
                : tab == R.id.navTimer ? R.layout.activity_alerts
                : tab == R.id.navMap ? R.layout.activity_map : R.layout.activity_main;
        setContentView(layout);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return insets;
        });
        ViewCompat.requestApplyInsets(findViewById(R.id.main));
        BottomNavigationView navigation = findViewById(R.id.bottomNavigation);
        navigation.setItemActiveIndicatorEnabled(false);
        navigation.setSelectedItemId(tab);
        navigation.setOnItemSelectedListener(item -> {
            if (currentTab != item.getItemId()) showPage(item.getItemId());
            return true;
        });
        findViewById(R.id.menuButton).setOnClickListener(this::showMenu);
        click(R.id.parkButton, () -> requestLocation(true));
        click(R.id.openMapButton, () -> showPage(R.id.navMap));
        click(R.id.updateLocationButton, () -> requestLocation(false));
        click(R.id.saveNotesButton, () -> saveDetails(true));
        click(R.id.selectPhotoButton, this::choosePhoto);
        click(R.id.takePhotoButton, this::takePhoto);
        click(R.id.previousPhotoButton, () -> { photoIndex--; renderPhoto(); });
        click(R.id.nextPhotoButton, () -> { photoIndex++; renderPhoto(); });
        click(R.id.removePhotoButton, this::removePhoto);
        click(R.id.endParkingButton, this::endParking);
        click(R.id.navigateButton, () -> ParkingMap.navigate(this, current()));
        click(R.id.extendTimeButton, () -> alerts.timer(state));
        click(R.id.alertExtendTimeButton, () -> alerts.timer(state));
        click(R.id.cancelTimerButton, () -> alerts.cancelTimer(state));
        click(R.id.reminderSettingsButton, alerts::settings);
        if (tab == R.id.navMap) parkingMap.attach(findViewById(R.id.mapContainer), current());
        renderState();
    }

    private void click(int id, Runnable action) {
        View view = findViewById(id);
        if (view != null) view.setOnClickListener(ignored -> action.run());
    }
    private void text(int id, String value) {
        TextView view = findViewById(id);
        if (view != null) view.setText(value);
    }
    private ParkingLocation current() { return state == null ? null : state.current; }
    private boolean requireParking() {
        if (current() != null) return true;
        message("Save a parking location first.");
        return false;
    }
    private void message(String text) {
        if (isDestroyed()) return;
        if (toast != null) toast.cancel();
        toast = Toast.makeText(this, text, Toast.LENGTH_LONG);
        toast.show();
    }

    // Render real data and empty states while leaving in-progress edits untouched.
    private void renderState() {
        ParkingLocation location = current();
        String address = location == null ? "No parking location saved" : location.address;
        String details = location == null ? "Tap the location button to park." : details(location);
        text(R.id.addressText, address);
        text(R.id.alertAddressText, address);
        text(R.id.parkingAddressText, address);
        text(R.id.parkingDetailsText, details);
        text(R.id.alertParkingDetailsText, details);
        text(R.id.locationStatusText, locating ? "Finding your location…" : "Tap to save a new parking spot");
        updateClock();
        if (currentTab == R.id.navTimer) alerts.renderStatus(state);
        if (currentTab == R.id.navParking) {
            boolean exists = location != null;
            for (int id : new int[]{R.id.notesInput, R.id.levelInput, R.id.spotInput,
                    R.id.saveNotesButton, R.id.selectPhotoButton, R.id.takePhotoButton, R.id.endParkingButton})
                findViewById(id).setEnabled(exists);
            if (editorLocationId != (exists ? location.id : 0)) {
                editorLocationId = exists ? location.id : 0;
                text(R.id.notesInput, exists ? location.notes : "");
                text(R.id.levelInput, exists ? location.garageLevel : "");
                text(R.id.spotInput, exists ? location.parkingSpot : "");
                photoIndex = 0;
            }
            renderPhoto();
        }
        if (currentTab == R.id.navMap) {
            parkingMap.update(location);
            text(R.id.mapStatusText, location == null ? "Save a parking location to see your car here." : address);
            findViewById(R.id.navigateButton).setEnabled(location != null);
        }
    }

    private static String details(ParkingLocation location) {
        StringBuilder value = new StringBuilder();
        if (!location.garageLevel.isEmpty()) value.append("Level ").append(location.garageLevel);
        if (!location.parkingSpot.isEmpty()) {
            if (value.length() > 0) value.append(" • ");
            value.append("Spot ").append(location.parkingSpot);
        }
        if (!location.notes.isEmpty()) {
            if (value.length() > 0) value.append(" • ");
            value.append(location.notes);
        }
        return value.length() == 0 ? "No parking notes added" : value.toString();
    }

    // Persist edits by their location ID, never by whichever location happens to be active later.
    private void persistDetails() { saveDetails(false); }
    private void saveDetails(boolean notify) {
        if (repository == null || currentTab != R.id.navParking || editorLocationId <= 0) return;
        EditText input = findViewById(R.id.notesInput);
        if (input == null) return;
        String notes = input.getText().toString().trim();
        String level = ((EditText) findViewById(R.id.levelInput)).getText().toString().trim();
        String spot = ((EditText) findViewById(R.id.spotInput)).getText().toString().trim();
        ParkingLocation old = current();
        if (old != null && old.notes.equals(notes) && old.garageLevel.equals(level) && old.parkingSpot.equals(spot)) {
            if (notify) message("Parking details are up to date.");
            return;
        }
        long id = editorLocationId;
        repository.change(dao -> {
            ParkingLocation location = dao.location(id);
            if (location != null) {
                location.notes = notes; location.garageLevel = level; location.parkingSpot = spot;
                dao.updateLocation(location);
            }
        }, error -> {
            if (error != null) message("Could not save parking details. Please try again.");
            else if (notify) message("Parking details saved.");
        });
    }

    // Request a fresh location only after the user explicitly starts or updates parking.
    private void requestLocation(boolean create) {
        if (state == null) { message("Parking data is still loading."); return; }
        if (locating) return;
        newParking = create || current() == null;
        if (hasLocationPermission()) locate();
        else locationPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
    }
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    private void locate() {
        locating = true;
        renderState();
        locationRequest = new CancellationTokenSource();
        long existingId = current() == null ? 0 : current().id;
        boolean create = newParking;
        try {
            CurrentLocationRequest request = new CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY).setMaxUpdateAgeMillis(0).setDurationMillis(20_000).build();
            LocationServices.getFusedLocationProviderClient(this).getCurrentLocation(request, locationRequest.getToken())
                    .addOnSuccessListener(this, location -> {
                        locating = false;
                        renderState();
                        if (location == null) { locationFailed(); return; }
                        saveLocation(location.getLatitude(), location.getLongitude(), create, existingId);
                    }).addOnFailureListener(this, error -> { locating = false; renderState(); locationFailed(); });
        } catch (SecurityException error) { locating = false; renderState(); locationFailed(); }
    }
    private void locationFailed() {
        new MaterialAlertDialogBuilder(this).setTitle("Location unavailable")
                .setMessage("Turn on Location in your device settings and try again, or enter the parking coordinates manually.")
                .setPositiveButton("Enter coordinates", (dialog, which) -> manualLocation())
                .setNegativeButton("Cancel", null).show();
    }

    // Save a new session atomically, or update the current session without losing its notes and timer.
    private void saveLocation(double latitude, double longitude, boolean create, long existingId) {
        persistDetails();
        long[] savedId = new long[1];
        repository.change(dao -> {
            ParkingLocation location = create ? null : dao.location(existingId);
            if (location == null) {
                dao.endCurrent();
                location = new ParkingLocation();
                location.savedAt = System.currentTimeMillis();
            }
            location.latitude = latitude;
            location.longitude = longitude;
            location.address = String.format(Locale.US, "%.6f, %.6f", latitude, longitude);
            if (location.id == 0) location.id = dao.insertLocation(location); else dao.updateLocation(location);
            savedId[0] = location.id;
        }, error -> {
            if (error != null) { message("Could not save the location. Please try again."); return; }
            if (isDestroyed()) return;
            showPage(R.id.navParking);
            message("Parking saved. Add a photo, level, spot, or notes.");
            resolveAddress(savedId[0], latitude, longitude);
        });
    }
    private void manualLocation() {
        if (state == null) return;
        FormDialog form = new FormDialog(this);
        int number = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
        EditText lat = form.field("Latitude (−90 to 90)", "", number, 20);
        EditText lon = form.field("Longitude (−180 to 180)", "", number, 20);
        form.show("Save parking coordinates", "Save", () -> {
            try {
                double latitude = Double.parseDouble(lat.getText().toString().trim());
                double longitude = Double.parseDouble(lon.getText().toString().trim());
                if (!LegacyParkingImporter.validCoordinates(latitude, longitude)) throw new NumberFormatException();
                saveLocation(latitude, longitude, true, 0);
                return true;
            } catch (NumberFormatException error) { lat.setError("Enter valid latitude and longitude values."); return false; }
        });
    }

    // Geocoding is optional; valid coordinates remain saved when the device is offline.
    @SuppressWarnings("deprecation")
    private void resolveAddress(long id, double latitude, double longitude) {
        PHOTOS.execute(() -> {
            try {
                List<Address> results = new Geocoder(getApplicationContext(), Locale.getDefault()).getFromLocation(latitude, longitude, 1);
                if (results == null || results.isEmpty()) return;
                String address = results.get(0).getAddressLine(0);
                if (address == null || address.trim().isEmpty()) return;
                repository.change(dao -> {
                    ParkingLocation location = dao.location(id);
                    if (location != null && location.latitude == latitude && location.longitude == longitude) {
                        location.address = address; dao.updateLocation(location);
                    }
                }, null);
            } catch (Exception ignored) { /* Retain the coordinate fallback if no geocoder is available. */ }
        });
    }

    // Multiple private photos belong to the selected session, including results returned after recreation.
    private void choosePhoto() {
        if (!requireParking()) return;
        photoLocationId = current().id;
        photoPicker.launch(new String[]{"image/*"});
    }
    private void takePhoto() {
        if (!requireParking()) return;
        photoLocationId = current().id;
        try {
            File folder = new File(getCacheDir(), "camera");
            if (!folder.exists() && !folder.mkdirs()) throw new java.io.IOException();
            cameraFile = File.createTempFile("parking-", ".jpg", folder);
            camera.launch(FileProvider.getUriForFile(this, getPackageName() + ".files", cameraFile));
        } catch (ActivityNotFoundException error) { message("No camera app is installed. Use Choose Photo instead."); }
        catch (java.io.IOException error) { message("Could not create the camera file."); }
    }
    private void savePhoto(Uri uri) {
        long locationId = photoLocationId;
        if (locationId <= 0) return;
        File pendingCamera = cameraFile;
        PHOTOS.execute(() -> {
            try {
                String path = PhotoStorage.importPhoto(getApplicationContext(), uri);
                repository.change(dao -> {
                    if (dao.location(locationId) == null) throw new IllegalStateException("Parking no longer exists");
                    ParkingPhoto photo = new ParkingPhoto();
                    photo.locationId = locationId; photo.uri = path; photo.createdAt = System.currentTimeMillis();
                    dao.insertPhoto(photo);
                }, error -> {
                    if (error != null) { PhotoStorage.delete(getApplicationContext(), path); message("Could not save the photo."); }
                    else { photoIndex = 0; renderPhoto(); message("Parking photo saved."); }
                });
            } catch (Exception error) { runOnUiThread(() -> message("This image could not be opened. Try another photo.")); }
            finally { if (pendingCamera != null && uri.equals(Uri.fromFile(pendingCamera))) pendingCamera.delete(); }
        });
    }
    private void renderPhoto() {
        if (currentTab != R.id.navParking || isDestroyed()) return;
        ImageView image = findViewById(R.id.parkingPhoto);
        int count = state == null ? 0 : state.photos.size();
        photoIndex = Math.max(0, Math.min(photoIndex, count - 1));
        text(R.id.photoCountText, count == 0 ? "No parking photos yet" : "Photo " + (photoIndex + 1) + " of " + count);
        findViewById(R.id.previousPhotoButton).setEnabled(photoIndex > 0);
        findViewById(R.id.nextPhotoButton).setEnabled(photoIndex + 1 < count);
        findViewById(R.id.removePhotoButton).setEnabled(count > 0);
        Object token = new Object(); image.setTag(token);
        image.setImageResource(R.drawable.ic_car);
        if (count == 0) return;
        ParkingPhoto photo = state.photos.get(photoIndex);
        PHOTOS.execute(() -> {
            try {
                Bitmap bitmap = PhotoStorage.decode(getApplicationContext(), photo.uri, 1024);
                runOnUiThread(() -> {
                    if (!isDestroyed() && image.getTag() == token) image.setImageBitmap(bitmap);
                    else bitmap.recycle();
                });
            } catch (Exception ignored) { /* A removed legacy gallery image falls back to the car icon. */ }
        });
    }
    private void removePhoto() {
        if (state == null || state.photos.isEmpty()) return;
        ParkingPhoto photo = state.photos.get(photoIndex);
        new MaterialAlertDialogBuilder(this).setTitle("Remove parking photo?")
                .setMessage("The original image in your gallery will be kept.")
                .setNegativeButton("Cancel", null).setPositiveButton("Remove", (dialog, which) -> repository.change(
                        dao -> dao.deletePhoto(photo.id), error -> {
                            if (error == null) PHOTOS.execute(() -> PhotoStorage.delete(getApplicationContext(), photo.uri));
                            else message("Could not remove the photo.");
                        })).show();
    }

    // The menu exposes the local profile and all previously saved parking locations.
    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Parking history").setOnMenuItemClickListener(item -> { history(); return true; });
        menu.getMenu().add("My profile").setOnMenuItemClickListener(item -> { profile(); return true; });
        menu.getMenu().add("Enter parking coordinates").setOnMenuItemClickListener(item -> { manualLocation(); return true; });
        menu.getMenu().add("Reminder settings").setOnMenuItemClickListener(item -> { alerts.settings(); return true; });
        menu.getMenu().add("About ParkMark").setOnMenuItemClickListener(item -> {
            new MaterialAlertDialogBuilder(this).setTitle("ParkMark")
                    .setMessage("Remember your parking location, photos, and notes. Parking data and your profile are stored on this device.")
                    .setPositiveButton("OK", null).show(); return true;
        });
        menu.show();
    }
    private void profile() {
        if (state == null) return;
        FormDialog form = new FormDialog(this);
        EditText first = form.text("First name", state.user.firstName);
        EditText last = form.text("Last name", state.user.lastName);
        EditText email = form.field("Email (optional)", state.user.email, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 254);
        form.show("My local profile", "Save", () -> {
            String value = email.getText().toString().trim();
            if (!value.isEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()) { email.setError("Enter a valid email address."); return false; }
            UserProfile user = new UserProfile();
            user.firstName = first.getText().toString().trim(); user.lastName = last.getText().toString().trim(); user.email = value;
            repository.change(dao -> dao.updateUser(user), error -> message(error == null ? "Profile saved on this device." : "Could not save the profile."));
            return true;
        });
    }
    private void history() {
        if (state == null || state.locations.isEmpty()) { message("No parking history yet."); return; }
        List<ParkingLocation> locations = new java.util.ArrayList<>(state.locations);
        String[] labels = new String[locations.size()];
        for (int i = 0; i < labels.length; i++) {
            ParkingLocation location = locations.get(i);
            labels[i] = (location.active ? "Current • " : "") + TimeText.dateTime(location.savedAt) + "\n" + location.address;
        }
        new MaterialAlertDialogBuilder(this).setTitle("Parking history").setItems(labels, (dialog, which) -> {
            ParkingLocation location = locations.get(which);
            new MaterialAlertDialogBuilder(this).setTitle(location.address)
                    .setMessage(TimeText.dateTime(location.savedAt) + "\n\n" + details(location))
                    .setPositiveButton("Directions", (d, w) -> ParkingMap.navigate(this, location))
                    .setNeutralButton("Photos", (d, w) -> historyPhotos(location.id))
                    .setNegativeButton("Close", null).show();
        }).setNegativeButton("Close", null).show();
    }
    // Read historical photos without making an old parking session current again.
    private void historyPhotos(long locationId) {
        ParkRepository.IO.execute(() -> {
            List<ParkingPhoto> photos = ParkDatabase.get(this).dao().photos(locationId);
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (photos.isEmpty()) { message("This parking session has no photos."); return; }
                showHistoricalPhoto(photos, 0);
            });
        });
    }
    private void showHistoricalPhoto(List<ParkingPhoto> photos, int index) {
        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setMinimumHeight(Math.round(240 * getResources().getDisplayMetrics().density));
        image.setImageResource(R.drawable.ic_car);
        image.setContentDescription("Parking photo " + (index + 1));
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("Photo " + (index + 1) + " of " + photos.size()).setView(image)
                .setNegativeButton("Close", null);
        if (index + 1 < photos.size()) builder.setPositiveButton("Next", (d, w) -> showHistoricalPhoto(photos, index + 1));
        if (index > 0) builder.setNeutralButton("Previous", (d, w) -> showHistoricalPhoto(photos, index - 1));
        androidx.appcompat.app.AlertDialog dialog = builder.show();
        PHOTOS.execute(() -> {
            try {
                Bitmap bitmap = PhotoStorage.decode(getApplicationContext(), photos.get(index).uri, 1024);
                runOnUiThread(() -> {
                    if (!isDestroyed() && dialog.isShowing()) image.setImageBitmap(bitmap); else bitmap.recycle();
                });
            } catch (Exception error) { runOnUiThread(() -> message("This saved photo is no longer available.")); }
        });
    }

    private void endParking() {
        if (!requireParking()) return;
        new MaterialAlertDialogBuilder(this).setTitle("Finish this parking session?")
                .setMessage("Your location, photos, and notes stay in parking history.")
                .setNegativeButton("Cancel", null).setPositiveButton("Finish", (dialog, which) -> {
                    persistDetails();
                    repository.change(ParkDao::endCurrent, error -> {
                        if (error == null && !isDestroyed()) showPage(R.id.navHome);
                        else if (error != null) message("Could not finish parking.");
                    });
                }).show();
    }

    // Refresh labels while visible; the persisted deadlines remain the source of truth.
    private void updateClock() {
        ParkingLocation location = current();
        long now = System.currentTimeMillis();
        String saved = location == null ? "Not parked yet" : TimeText.saved(location.savedAt, now);
        text(R.id.savedTimeText, saved); text(R.id.alertSavedTimeText, saved);
        String remaining = TimeText.remaining(state == null || state.timer == null ? 0 : state.timer.expirationTime, now);
        text(R.id.timeRemainingText, remaining); text(R.id.parkingTimeLeftText, remaining);
    }
    private final Runnable ticker = new Runnable() {
        @Override public void run() { updateClock(); handler.postDelayed(this, 1000); }
    };
    @Override protected void onStart() { super.onStart(); parkingMap.start(); }
    @Override protected void onResume() {
        super.onResume(); parkingMap.resume(); handler.removeCallbacks(ticker); handler.post(ticker);
        repository.refresh(error -> { if (error != null) message("Could not refresh parking data."); });
    }
    private void requestNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (intent.getBooleanExtra("openAlerts", false)) showPage(R.id.navTimer);
    }
    @Override protected void onPause() { persistDetails(); handler.removeCallbacks(ticker); parkingMap.pause(); super.onPause(); }
    @Override protected void onStop() {
        if (locationRequest != null) locationRequest.cancel();
        locating = false;
        parkingMap.stop(); super.onStop();
    }
    @Override protected void onDestroy() { handler.removeCallbacks(ticker); parkingMap.destroy(); super.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); parkingMap.lowMemory(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        persistDetails();
        out.putInt("tab", currentTab); out.putLong("photoLocation", photoLocationId); out.putBoolean("newParking", newParking);
        if (cameraFile != null) out.putString("cameraFile", cameraFile.getAbsolutePath());
        super.onSaveInstanceState(out);
    }
}
