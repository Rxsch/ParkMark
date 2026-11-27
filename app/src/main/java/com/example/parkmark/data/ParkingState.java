package com.example.parkmark.data;

import java.util.List;

// Publish a consistent snapshot after a completed database transaction.
public class ParkingState {
    public final UserProfile user;
    public final ParkingLocation current;
    public final List<ParkingLocation> locations;
    public final List<ParkingPhoto> photos;
    public final ParkingTimer timer;
    public final List<ParkingPermit> permits;

    ParkingState(ParkDao dao) {
        user = dao.getUser();
        current = dao.current();
        locations = dao.locations();
        photos = dao.photos(current == null ? -1 : current.id);
        timer = current == null ? null : dao.timer(current.id);
        permits = dao.permits();
    }
}
