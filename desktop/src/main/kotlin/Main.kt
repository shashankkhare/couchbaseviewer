import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.couchbaseviewer.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Couchbase Viewer"
    ) {
        App()
    }
}
