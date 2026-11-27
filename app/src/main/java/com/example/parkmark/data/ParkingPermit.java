package com.example.parkmark.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

// Store one record from the ParkMark design's relational model.
@Entity(tableName = "parking_permits", foreignKeys = @ForeignKey(
        entity = UserProfile.class, parentColumns = "id", childColumns = "userId",
        onDelete = ForeignKey.CASCADE), indices = @Index("userId"))
public class ParkingPermit {
    @PrimaryKey(autoGenerate = true) public long id;
    public long userId = 1;
    @NonNull public String name = "";
    public long expirationDate;
    public long reminderDate;
    public boolean notificationEnabled = true;
    public boolean reminderDelivered;
    public boolean expirationDelivered;
}
