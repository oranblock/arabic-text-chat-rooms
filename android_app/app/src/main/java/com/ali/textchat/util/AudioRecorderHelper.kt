package com.ali.textchat.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0L

    var isRecording: Boolean = false
        private set

    fun start(): Boolean {
        return try {
            cancel()
            val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
            currentFile = file

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            startTimeMs = System.currentTimeMillis()
            isRecording = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            cancel()
            false
        }
    }

    fun stop(): Pair<File?, Int> {
        if (!isRecording) return Pair(null, 0)
        isRecording = false
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            val durationSec = Math.max(1, ((System.currentTimeMillis() - startTimeMs) / 1000).toInt())
            Pair(currentFile, durationSec)
        } catch (e: Exception) {
            e.printStackTrace()
            recorder = null
            Pair(null, 0)
        }
    }

    fun cancel() {
        isRecording = false
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {}
        recorder = null
        currentFile?.delete()
        currentFile = null
    }
}
