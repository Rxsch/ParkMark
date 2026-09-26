package com.example.parkmark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.parkmark.ui.theme.ParkMarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParkMarkTheme {
                SimpleApp()
            }
        }
    }
}

@Composable
fun SimpleApp() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Hello World!",
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = "My name is\nDaniel Rangosch Montero\n",
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = "My favorite color is\nBlack.\n",
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = "My Lucky Number is\n 24.\n",
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = "More to follow!",
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
    }
}