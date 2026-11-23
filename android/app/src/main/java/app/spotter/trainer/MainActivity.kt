package app.spotter.trainer

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Hosts the Spotter web app (bundled in assets/) in a WebView and exposes
 * window.SpotterNative so the page can use Health Connect and save files.
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var health: HealthBridge

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    private var pendingPermissions: CompletableDeferred<Set<String>>? = null
    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted -> pendingPermissions?.complete(granted) }

    private var pendingSave: Pair<String, CompletableDeferred<Boolean>>? = null
    private val saveLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> finishSave(uri) }

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val openLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        health = HealthBridge(this)

        webView = WebView(this)
        setContentView(webView)
        // Keep the page clear of the status bar, navigation bar and keyboard
        ViewCompat.setOnApplyWindowInsetsListener(webView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val pad = Insets.max(bars, Insets.of(0, 0, 0, ime.bottom))
            v.setPadding(pad.left, pad.top, pad.right, pad.bottom)
            WindowInsetsCompat.CONSUMED
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
        }
        webView.setBackgroundColor(getColor(R.color.spotter_bg))
        webView.addJavascriptInterface(Bridge(), "SpotterNative")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == APP_HOST) return false
                // Links to other sites open in the browser
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return runCatching { openLauncher.launch("*/*"); true }.getOrElse { fileCallback = null; false }
            }
        }

        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
        else webView.loadUrl("https://$APP_HOST/assets/index.html")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    /** Called from the page: SpotterNative.call(id, method, argsJson). Replies via window.__spotterNativeResolve. */
    inner class Bridge {
        @JavascriptInterface
        fun call(id: String, method: String, argsJson: String) {
            lifecycleScope.launch {
                val reply = JSONObject()
                try {
                    val args = if (argsJson.isBlank()) JSONObject() else JSONObject(argsJson)
                    reply.put("ok", true).put("data", dispatch(method, args))
                } catch (e: Exception) {
                    reply.put("ok", false).put("error", e.message ?: e.javaClass.simpleName)
                }
                webView.evaluateJavascript(
                    "window.__spotterNativeResolve && window.__spotterNativeResolve(${JSONObject.quote(id)}, $reply)", null
                )
            }
        }
    }

    private suspend fun dispatch(method: String, args: JSONObject): Any = when (method) {
        "status" -> health.status()
        "requestPermissions" -> {
            if (!health.isAvailable()) throw IllegalStateException("Health Connect isn't available on this phone")
            val deferred = CompletableDeferred<Set<String>>()
            pendingPermissions = deferred
            permissionLauncher.launch(HealthBridge.PERMISSIONS)
            JSONObject().put("granted", org.json.JSONArray(deferred.await().toList()))
        }
        "readiness" -> health.readiness()
        "sessionHeartRate" -> health.sessionHeartRate(args.getString("start"), args.getString("end"))
        "writeWorkout" -> health.writeWorkout(
            args.getString("start"), args.getString("end"), args.optString("title"), args.optString("notes")
        )
        "openHealthConnect" -> { health.openHealthConnect(this); JSONObject() }
        "saveFile" -> {
            val deferred = CompletableDeferred<Boolean>()
            pendingSave = args.getString("text") to deferred
            saveLauncher.launch(args.optString("filename", "spotter-backup.json"))
            JSONObject().put("saved", deferred.await())
        }
        else -> throw IllegalArgumentException("Unknown method $method")
    }

    private fun finishSave(uri: Uri?) {
        val (text, deferred) = pendingSave ?: return
        pendingSave = null
        if (uri == null) { deferred.complete(false); return }
        val ok = runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
        }.getOrDefault(false)
        deferred.complete(ok)
    }

    companion object {
        const val APP_HOST = "appassets.androidplatform.net"
    }
}
