package com.example.parkmark.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

// Store one record from the ParkMark design's relational model.
@Entity(tableName = "parking_locations", foreignKeys = @ForeignKey(
        entity = UserProfile.class, parentColumns = "id", childColumns = "userId",
        onDelete = ForeignKey.CASCADE), indices = @Index("userId"))
public class ParkingLocation {
    @PrimaryKey(autoGenerate = true) public long id;
    public long userId = 1;
    public double latitude;
    public double longitude;
    @NonNull public String address = "";
    @NonNull public String garageLevel = "";
    @NonNull public String parkingSpot = "";
    @NonNull public String notes = "";
    public long savedAt;
    public boolean active = true;
}
