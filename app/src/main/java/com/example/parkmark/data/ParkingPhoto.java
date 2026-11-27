package com.example.parkmark.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

// Store one record from the ParkMark design's relational model.
@Entity(tableName = "photos", foreignKeys = @ForeignKey(
        entity = ParkingLocation.class, parentColumns = "id", childColumns = "locationId",
        onDelete = ForeignKey.CASCADE), indices = @Index("locationId"))
public class ParkingPhoto {
    @PrimaryKey(autoGenerate = true) public long id;
    public long locationId;
    @NonNull public String uri = "";
    public long createdAt;
    // Zero means keep the photo until the user removes it.
    public long deleteAfter;
}
