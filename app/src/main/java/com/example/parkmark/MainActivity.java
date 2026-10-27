package com.example.parkmark;

import android.os.Bundle;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private int currentTab = R.id.navHome;
    private String parkingNotes = "";

    // Load the initial page and restore temporary UI state.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

    // Display the selected page and reconnect its navigation.
    private void showPage(int tabId) {
        currentTab = tabId;

        if (tabId == R.id.navParking) {
            setContentView(R.layout.activity_parking);

            EditText notesInput = findViewById(R.id.notesInput);
            notesInput.setText(parkingNotes);

        } else if (tabId == R.id.navTimer) {
            setContentView(R.layout.activity_alerts);

        } else {
            currentTab = R.id.navHome;
            setContentView(R.layout.activity_main);
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
                    && selectedTab != R.id.navTimer) {
                return false;
            }

            if (selectedTab != currentTab) {
                rememberNotes();
                showPage(selectedTab);
            }

            return true;
        });
    }

    // Keep the typed notes when leaving the Parking page.
    private void rememberNotes() {
        if (currentTab == R.id.navParking) {
            EditText notesInput = findViewById(R.id.notesInput);
            parkingNotes = notesInput.getText().toString();
        }
    }

    // Preserve the current tab and notes during activity recreation.
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        rememberNotes();

        outState.putInt("currentTab", currentTab);
        outState.putString("parkingNotes", parkingNotes);

        super.onSaveInstanceState(outState);
    }
}