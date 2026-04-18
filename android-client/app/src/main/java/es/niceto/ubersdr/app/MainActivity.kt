package es.niceto.ubersdr.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import es.niceto.ubersdr.data.network.ConnectionApi
import es.niceto.ubersdr.data.network.ConnectionService
import es.niceto.ubersdr.data.instances.InstanceDirectoryRepository
import es.niceto.ubersdr.data.instances.InstanceDirectoryService
import es.niceto.ubersdr.presentation.radio.RadioViewModel
import es.niceto.ubersdr.session.SessionRepository
import es.niceto.ubersdr.ui.radio.RadioScreen
import es.niceto.ubersdr.ui.theme.UberSdrTheme
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://ubersdr.niceto.es/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val api = retrofit.create(ConnectionApi::class.java)
        val service = ConnectionService(api)
        val instanceDirectoryService = InstanceDirectoryService(api)
        val instanceDirectoryRepository = InstanceDirectoryRepository(instanceDirectoryService)
        val sessionRepository = SessionRepository(service)
        val settingsStore = AppSettingsStore(applicationContext)
        val viewModel = RadioViewModel(
            sessionRepository = sessionRepository,
            settingsStore = settingsStore,
            instanceDirectoryRepository = instanceDirectoryRepository
        )

        setContent {
            UberSdrTheme {
                Surface {
                    RadioScreen(viewModel = viewModel)
                }
            }
        }
    }
}
