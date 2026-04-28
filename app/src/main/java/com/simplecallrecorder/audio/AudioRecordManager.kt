package com.simplecallrecorder.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Environment
import android.util.Log
import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecordManager {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var currentOutputFile: File? = null

    companion object {
        private const val TAG = "AudioRecordManager"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val RECORDINGS_DIR = "Documents/MojeNahravky"
    }

    fun startRecording(callType: String, phoneNumber: String): File? {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return null
        }

        val outputFile = createOutputFile(callType, phoneNumber) ?: return null
        currentOutputFile = outputFile

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return null
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for AudioRecord", e)
            return null
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized")
            audioRecord?.release()
            audioRecord = null
            return null
        }

        isRecording = true
        audioRecord?.startRecording()

        recordingThread = Thread {
            encodeToMp3(outputFile, bufferSize)
        }.also { it.start() }

        Log.d(TAG, "Started recording to: ${outputFile.absolutePath}")
        return outputFile
    }

    fun stopRecording(): File? {
        if (!isRecording) return currentOutputFile

        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingThread?.join(3000)
        recordingThread = null

        Log.d(TAG, "Stopped recording: ${currentOutputFile?.absolutePath}")
        return currentOutputFile
    }

    fun isRecording(): Boolean = isRecording

    private fun encodeToMp3(outputFile: File, bufferSize: Int) {
        val buffer = ShortArray(bufferSize)
        val mp3Buffer = ByteArray((7200 + bufferSize * 2 * 1.25).toInt())

        val lame: AndroidLame = LameBuilder()
            .setInSampleRate(SAMPLE_RATE)
            .setOutChannels(1)
            .setOutBitrate(128)
            .setOutSampleRate(SAMPLE_RATE)
            .build()

        try {
            FileOutputStream(outputFile).use { fos ->
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: break
                    if (read > 0) {
                        val encoded = lame.encode(buffer, buffer, read, mp3Buffer)
                        if (encoded > 0) {
                            fos.write(mp3Buffer, 0, encoded)
                        }
                    }
                }
                // Flush remaining
                val flushed = lame.flush(mp3Buffer)
                if (flushed > 0) {
                    fos.write(mp3Buffer, 0, flushed)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding to MP3", e)
        } finally {
            lame.close()
        }
    }

    private fun createOutputFile(callType: String, phoneNumber: String): File? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "MojeNahravky"
        )
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Failed to create recordings directory")
            return null
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH-mm-ss", Locale.getDefault())
        val now = Date()
        val date = dateFormat.format(now)
        val time = timeFormat.format(now)

        val safeNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            .take(20)
            .ifEmpty { "unknown" }
        val safeType = callType.lowercase(Locale.getDefault())
            .replace(Regex("[^a-z]"), "")
            .take(10)
            .ifEmpty { "unknown" }

        val fileName = "${safeType}-${safeNumber}-${date}-${time}.mp3"
        return File(dir, fileName)
    }
}
