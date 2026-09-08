package de.lunara.app.ui.scanner

import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class LunaraScanOptionsTest {
    @Test
    fun `scanner uses branded adaptive qr configuration`() {
        val prompt = "Einladung scannen"
        val options = lunaraQrScanOptions(prompt)

        assertEquals(LunaraCaptureActivity::class.java, options.captureActivity)
        assertEquals(listOf(ScanOptions.QR_CODE), LUNARA_SCAN_FORMATS)
        assertEquals(false, options.moreExtras[Intents.Scan.ORIENTATION_LOCKED])
        assertEquals(false, options.moreExtras[Intents.Scan.BEEP_ENABLED])
        assertEquals(prompt, options.moreExtras[Intents.Scan.PROMPT_MESSAGE])
        assertEquals(false, options.moreExtras[Intents.Scan.SHOW_MISSING_CAMERA_PERMISSION_DIALOG])
        assertEquals(null, options.moreExtras[Intents.Scan.MISSING_CAMERA_PERMISSION_DIALOG_MESSAGE])
    }
}
