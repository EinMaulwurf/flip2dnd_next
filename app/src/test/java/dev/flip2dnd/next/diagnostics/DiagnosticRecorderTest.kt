package dev.flip2dnd.next.diagnostics

import android.app.Application
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DiagnosticRecorderTest {
    private lateinit var context: Application
    private lateinit var recorder: DiagnosticRecorder

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions("${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        recorder = DiagnosticRecorder(context)
    }
    @After fun cleanup() = runBlocking { recorder.close() }

    @Test fun recordingIsOffByDefaultAndDoesNotPersistEvents() = runBlocking {
        recorder.record("must_not_be_recorded")
        val lines = recorder.export().readLines()
        assertFalse(recorder.enabled.value)
        assertEquals(1, lines.size)
        assertFalse(lines.first().contains("must_not_be_recorded"))
    }

    @Test fun exportFlushesEventsAndDisablePreservesEarlierDataWithoutRecordingNewEvents() = runBlocking {
        recorder.setEnabled(true)
        recorder.record("flip_test", "orientation" to "FACE_DOWN")
        recorder.setEnabled(false)
        recorder.record("disabled_event")
        val report = recorder.export().readText()
        assertTrue(report.contains("flip_test"))
        assertTrue(report.contains("recording_disabled"))
        assertFalse(report.contains("disabled_event"))
        report.lineSequence().filter { it.isNotBlank() }.forEach { JSONObject(it) }
    }

    @Test fun recordingAndEventsSurviveProcessRecreation() = runBlocking {
        recorder.setEnabled(true)
        recorder.record("before_restart")
        recorder.close()
        recorder = DiagnosticRecorder(context)
        assertTrue(recorder.enabled.value)
        val report = recorder.export().readText()
        assertTrue(report.contains("before_restart"))
        assertTrue(report.contains("process_started"))
    }

    @Test fun snapshotsExcludeCustomSoundUrisAndUnrelatedPreferences() = runBlocking {
        context.getSharedPreferences("flip_2_dnd_settings", Context.MODE_PRIVATE).edit()
            .putString("dnd_on_custom_sound_uri", "content://private/secret-filename.mp3")
            .putString("unrelated_private_value", "private-text")
            .putBoolean("proximity_detection", true).commit()
        recorder.setEnabled(true)
        val report = recorder.export().readText()
        assertFalse(report.contains("secret-filename"))
        assertFalse(report.contains("private-text"))
        assertFalse(report.contains("dnd_on_custom_sound_uri"))
        assertTrue(report.contains("proximity_detection"))
    }

    @Test fun clearingAlsoRemovesPreviouslyExportedReports() = runBlocking {
        recorder.setEnabled(true)
        recorder.record("old_event")
        val oldExport = recorder.export()
        recorder.clear()
        val report = recorder.export().readText()
        assertFalse(oldExport.exists())
        assertFalse(report.contains("old_event"))
        assertTrue(report.contains("log_cleared"))
    }

    @Test fun storageFailureIsReportedWithoutBreakingTheWriterOrMetadataExport() = runBlocking {
        java.io.File(context.noBackupFilesDir, "diagnostics").writeText("blocks directory creation")
        try { recorder.setEnabled(true); fail("Expected unavailable storage") }
        catch (_: java.io.IOException) { }
        recorder.record("cannot_write")
        val header = JSONObject(recorder.export().readLines().first())
        assertTrue(recorder.storageError.value)
        assertTrue(header.getBoolean("storage_error"))
        assertTrue(header.has("snapshot"))
    }

    @Test fun exportsAreBoundedAndSharedThroughAReadOnlyContentUri() = runBlocking {
        repeat(6) { recorder.export() }
        val exports = java.io.File(context.cacheDir, "diagnostic_exports").listFiles()!!
        assertEquals(3, exports.size)
        val report = recorder.export()
        val intent = DiagnosticSharing.intent(context, report)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.diagnostics", uri.authority)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertFalse(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        assertTrue(text.contains("flip2dnd-diagnostics-v1"))
    }
}
