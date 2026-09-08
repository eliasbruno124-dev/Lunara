package de.lunara.app.ui.scanner

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import de.lunara.app.R

class LunaraCaptureActivity : CaptureActivity(), DecoratedBarcodeView.TorchListener {
    private lateinit var scannerView: DecoratedBarcodeView
    private var torchButton: ImageButton? = null
    private var torchEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
    }

    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.activity_lunara_scanner)
        scannerView = findViewById(R.id.zxing_barcode_scanner)
        scannerView.setTorchListener(this)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scanner_root)) { view, insets ->
            val safeArea = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
            insets
        }
        findViewById<TextView>(R.id.scanner_context_prompt)?.text =
            intent.getStringExtra(Intents.Scan.PROMPT_MESSAGE) ?: getString(R.string.scan_qr_prompt)

        findViewById<View>(R.id.scanner_close).setOnClickListener { finish() }
        torchButton = findViewById<ImageButton>(R.id.scanner_torch).also { button ->
            button.visibility = if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                View.VISIBLE
            } else {
                View.GONE
            }
            button.setOnClickListener {
                if (torchEnabled) scannerView.setTorchOff() else scannerView.setTorchOn()
            }
        }
        updateTorchButton()
        return scannerView
    }

    override fun onTorchOn() {
        torchEnabled = true
        updateTorchButton()
    }

    override fun onTorchOff() {
        torchEnabled = false
        updateTorchButton()
    }

    private fun updateTorchButton() {
        torchButton?.apply {
            isActivated = torchEnabled
            imageAlpha = if (torchEnabled) 255 else 176
            contentDescription = getString(
                if (torchEnabled) R.string.scanner_flashlight_off else R.string.scanner_flashlight_on,
            )
        }
    }
}
