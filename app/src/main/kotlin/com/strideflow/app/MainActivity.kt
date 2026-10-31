package com.strideflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.strideflow.app.ui.theme.StrideFlowTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val storage = com.strideflow.app.data.LocalStorageManager(this)
    val plan = storage.loadActivePlan()
    val profile = storage.loadProfile()
    com.strideflow.app.service.NutritionNotificationScheduler.syncNotifications(this, plan, profile)

    enableEdgeToEdge()
    setContent {
      StrideFlowTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }
}
