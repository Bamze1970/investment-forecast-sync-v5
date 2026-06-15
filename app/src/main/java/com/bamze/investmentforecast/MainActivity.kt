package com.bamze.investmentforecast

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                InvestmentForecastApp()
            }
        }
    }
}

private const val PREFS = "investment_forecast_prefs"
private const val KEY_URL = "web_url"

private fun currentUrl(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return prefs.getString(KEY_URL, BuildConfig.DEFAULT_WEB_URL) ?: BuildConfig.DEFAULT_WEB_URL
}

private fun saveUrl(context: Context, value: String) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_URL, value.trim())
        .apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentForecastApp() {
    val context = LocalContext.current
    var url by rememberSaveable { mutableStateOf(currentUrl(context)) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var pageTitle by rememberSaveable { mutableStateOf("Investment Forecast") }
    var pageProgress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(pageTitle)
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { webViewRef?.goBack() }, enabled = canGoBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обнови")
                    }
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Отвори в браузър")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (pageProgress in 0..99) {
                LinearProgressIndicator(
                    progress = { pageProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
            }
            ForecastWebView(
                url = url,
                onTitleChange = { pageTitle = it.ifBlank { "Investment Forecast" } },
                onProgressChange = { pageProgress = it },
                onCanGoBackChange = { canGoBack = it },
                onRef = { webViewRef = it }
            )
        }
    }

    if (showSettings) {
        UrlSettingsDialog(
            currentUrl = url,
            onDismiss = { showSettings = false },
            onSave = { newUrl ->
                saveUrl(context, newUrl)
                url = newUrl
                showSettings = false
                scope.launch { webViewRef?.loadUrl(newUrl) }
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ForecastWebView(
    url: String,
    onTitleChange: (String) -> Unit,
    onProgressChange: (Int) -> Unit,
    onCanGoBackChange: (Boolean) -> Unit,
    onRef: (WebView) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = false

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        onCanGoBackChange(view?.canGoBack() == true)
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        onTitleChange(title ?: "Investment Forecast")
                    }

                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        onProgressChange(newProgress)
                    }
                }
                loadUrl(url)
                onRef(this)
            }
        },
        update = { webView ->
            onRef(webView)
            if (webView.url != url) webView.loadUrl(url)
        }
    )
}

@Composable
private fun UrlSettingsDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Приложението зарежда уеб версията на Investment Forecast. Можеш да смениш URL, ако публикуваш по-нова версия (например v4).")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Web URL") }
                )
                Text("Препоръчителен URL: https://bamze1970.github.io/investment-forecast-sync-v4/")
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text) }) { Text("Запази") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отказ") }
        }
    )
}
