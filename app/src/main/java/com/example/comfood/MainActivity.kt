package com.example.comfood

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.comfood.ui.ComFoodApp
import com.example.comfood.ui.theme.ComFoodTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComFoodTheme {
                ComFoodApp(activity = this)
            }
        }
    }
}
