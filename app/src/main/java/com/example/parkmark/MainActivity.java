package com.example.parkmark;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private int currentTab = R.id.navHome;
    private String parkingNotes = "";

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

    private void showPage(int tabId) {
        currentTab = tabId;

        if (tabId == R.id.navParking) {
            setContentView(R.layout.activity_parking);

            EditText notesInput = findViewById(R.id.notesInput);
            notesInput.setText(parkingNotes);

        } else if (tabId == R.id.navTimer) {
            setContentView(R.layout.activity_alerts);

        } else if (tabId == R.id.navMap) {
            setContentView(R.layout.activity_map);

            findViewById(R.id.navigateButton).setOnClickListener(view -> {
                Toast.makeText(
                        this,
                        "Navigation will be available in the final version",
                        Toast.LENGTH_SHORT
                ).show();
            });

        } else {
            currentTab = R.id.navHome;
            setContentView(R.layout.activity_main);

            findViewById(R.id.openMapButton)
                    .setOnClickListener(view -> showPage(R.id.navMap));
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
}