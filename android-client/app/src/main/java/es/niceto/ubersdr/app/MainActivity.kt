package es.niceto.ubersdr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import es.niceto.ubersdr.presentation.radio.RadioViewModel
import es.niceto.ubersdr.ui.radio.RadioScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModel = RadioViewModel()

        setContent {
            MaterialTheme {
                Surface {
                    RadioScreen(viewModel = viewModel)
                }
            }
        }
    }
}
