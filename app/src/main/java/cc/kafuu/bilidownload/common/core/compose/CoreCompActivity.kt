package cc.kafuu.bilidownload.common.core.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cc.kafuu.bilidownload.common.core.compose.theme.AppTheme

abstract class CoreCompActivity : ComponentActivity() {
    protected open fun isEnableEdgeToEdge(): Boolean = true

    @Composable
    protected abstract fun ViewContent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initView()
    }

    private fun initView() {
        if (isEnableEdgeToEdge()) {
            enableEdgeToEdge()
        }
        setContent { AppTheme(content = getContent()) }
    }

    private fun getContent(): @Composable () -> Unit = {
        Surface(modifier = Modifier.fillMaxSize()) { ViewContent() }
    }
}

@Composable
fun ActivityPreview(darkTheme: Boolean, content: @Composable () -> Unit) {
    AppTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize(), content = content)
    }
}