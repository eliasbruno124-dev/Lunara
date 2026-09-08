package de.lunara.app.ui.scanner

import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanOptions

internal val LUNARA_SCAN_FORMATS: List<String> = listOf(ScanOptions.QR_CODE)

internal fun lunaraQrScanOptions(
    prompt: String,
): ScanOptions = ScanOptions()
    .setCaptureActivity(LunaraCaptureActivity::class.java)
    .setDesiredBarcodeFormats(LUNARA_SCAN_FORMATS)
    .setPrompt(prompt)
    .setBeepEnabled(false)
    .setOrientationLocked(false)
    .addExtra(Intents.Scan.SHOW_MISSING_CAMERA_PERMISSION_DIALOG, false)
