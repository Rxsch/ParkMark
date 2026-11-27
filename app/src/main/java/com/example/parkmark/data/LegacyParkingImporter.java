package com.example.parkmark.data;

import android.content.SharedPreferences;

// Import the earlier SharedPreferences version exactly once, within the database transaction.
public final class LegacyParkingImporter {
    private LegacyParkingImporter() { }

    public static void migrate(ParkDao dao, SharedPreferences preferences) {
        if (dao.getUser() != null) return;
        dao.insertUser(new UserProfile());
        double latitude = Double.longBitsToDouble(preferences.getLong("latitude",
                Double.doubleToRawLongBits(Double.NaN)));
        double longitude = Double.longBitsToDouble(preferences.getLong("longitude",
                Double.doubleToRawLongBits(Double.NaN)));
        if (!validCoordinates(latitude, longitude)) return;

        ParkingLocation location = new ParkingLocation();
        location.latitude = latitude;
        location.longitude = longitude;
        location.address = preferences.getString("parkingAddress", "");
        location.notes = preferences.getString("parkingNotes", "");
        location.savedAt = preferences.getLong("parkingSavedTime", System.currentTimeMillis());
        location.id = dao.insertLocation(location);
        String uri = preferences.getString("parkingPhotoUri", "");
        if (!uri.isEmpty()) {
            ParkingPhoto photo = new ParkingPhoto();
            photo.locationId = location.id;
            photo.uri = uri;
            photo.createdAt = location.savedAt;
            dao.insertPhoto(photo);
        }
    }

    // Reject invalid coordinates without treating the equator or prime meridian as missing data.
    public static boolean validCoordinates(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isInfinite(latitude)
                && !Double.isNaN(longitude) && !Double.isInfinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }
}
