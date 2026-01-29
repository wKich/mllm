package com.mllm.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mllm.chat.ui.chat.ChatScreen
import com.mllm.chat.ui.settings.SettingsScreen
import com.mllm.chat.ui.theme.AIChatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AIChatNavigation()
                }
            }
        }
    }
}

@Composable
fun AIChatNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "chat"
    ) {
        composable("chat") {
            ChatScreen(
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
