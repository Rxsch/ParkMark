package com.example.parkmark.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

// Store one record from the ParkMark design's relational model.
@Entity(tableName = "users")
public class UserProfile {
    @PrimaryKey public long id = 1;
    @NonNull public String firstName = "";
    @NonNull public String lastName = "";
    @NonNull public String email = "";
}
