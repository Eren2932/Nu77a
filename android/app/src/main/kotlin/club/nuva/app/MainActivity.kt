package club.nuva.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import club.nuva.app.ui.NuvaApp
import club.nuva.app.ui.theme.NuvaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            NuvaTheme {
                NuvaApp()
            }
        }
    }
}
