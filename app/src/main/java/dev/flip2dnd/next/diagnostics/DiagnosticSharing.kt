package dev.flip2dnd.next.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object DiagnosticSharing {
    fun intent(context: Context, report: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", report)
        return Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newUri(context.contentResolver, "Diagnostic log", uri) }
    }
}
