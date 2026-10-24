package dev.flip2dnd.next.core

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile

/** Bounded, process-restart-safe journal. Callers supply complete single-line records. */
class DiagnosticJournal(
    private val directory: File,
    private val bytesPerFile: Int = 512 * 1024,
    private val fileCount: Int = 4,
) {
    init { require(bytesPerFile > 0 && fileCount > 0) }
    private var recovered = false
    private fun file(index: Int) = File(directory, "events.$index.jsonl")

    @Synchronized
    fun append(line: String): Boolean {
        recoverIncompleteRecord()
        val bytes = (line.replace('\n', ' ').replace('\r', ' ') + "\n").toByteArray(Charsets.UTF_8)
        if (bytes.size > bytesPerFile) return false
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create diagnostic directory")
        if (file(0).length() + bytes.size > bytesPerFile) {
            delete(file(fileCount - 1))
            for (index in fileCount - 2 downTo 0) {
                val source = file(index)
                if (source.exists() && !source.renameTo(file(index + 1))) throw IOException("Cannot rotate diagnostics")
            }
        }
        file(0).appendBytes(bytes)
        return true
    }

    @Synchronized
    fun writeTo(output: OutputStream) {
        recoverIncompleteRecord()
        for (index in fileCount - 1 downTo 0) {
            val source = file(index)
            if (source.exists()) source.inputStream().use { it.copyTo(output) }
        }
    }

    @Synchronized
    fun clear() { for (index in 0 until fileCount) delete(file(index)) }

    private fun recoverIncompleteRecord() {
        if (recovered) return
        val active = file(0)
        if (active.exists()) RandomAccessFile(active, "rw").use { data ->
            var end = data.length()
            while (end > 0) {
                data.seek(end - 1)
                if (data.readByte() == '\n'.code.toByte()) break
                end--
            }
            if (end != data.length()) data.setLength(end)
        }
        recovered = true
    }

    private fun delete(file: File) {
        if (file.exists() && !file.delete()) throw IOException("Cannot remove old diagnostics")
    }
}
