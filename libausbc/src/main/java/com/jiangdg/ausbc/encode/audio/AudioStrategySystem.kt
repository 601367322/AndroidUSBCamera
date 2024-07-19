/*
 * Copyright 2017-2023 Jiangdg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jiangdg.ausbc.encode.audio

import android.annotation.SuppressLint
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import com.jiangdg.ausbc.encode.bean.RawData
import com.jiangdg.ausbc.utils.Logger
import com.jiangdg.ausbc.utils.Utils

/** System audio record
 *
 * @author Created by jiangdg on 2022/9/14
 */
class AudioStrategySystem(config: RecordConfig) : IAudioStrategy {
    private val mBufferSize: Int by lazy {
        AudioRecord.getMinBufferSize(
            config.sampleRate,
            config.channelConfig,
            config.encodingConfig
        )
    }
    private var mAudioRecord: AudioRecord? = null
    private var mConfig: RecordConfig = config
    private var mAcousticEchoCanceler: AcousticEchoCanceler? = null
    private var mNoiseSuppressor: NoiseSuppressor? = null
    private var mAutomaticGainControl: AutomaticGainControl? = null

    @SuppressLint("MissingPermission")
    override fun initAudioRecord() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            mAudioRecord = AudioRecord(
                AUDIO_RECORD_SOURCE, mConfig.sampleRate,
                mConfig.channelConfig, mConfig.encodingConfig, mBufferSize
            )
            if (mConfig.isEnableAEC() && AcousticEchoCanceler.isAvailable()) {
                mAcousticEchoCanceler = AcousticEchoCanceler
                    .create(mAudioRecord!!.audioSessionId)
                val resultCode = mAcousticEchoCanceler!!.setEnabled(true)
                if (AudioEffect.SUCCESS == resultCode) {
                    Logger.d(TAG, "aec-->success")
                }
            }

            if (mConfig.isEnableNS() && NoiseSuppressor.isAvailable()) {
                mNoiseSuppressor = NoiseSuppressor
                    .create(mAudioRecord!!.audioSessionId)
                val resultCode = mNoiseSuppressor!!.setEnabled(true)
                if (AudioEffect.SUCCESS == resultCode) {
                    Logger.d(TAG, "ns-->success")
                }
            }

            if (mConfig.isEnableAGC() && AutomaticGainControl.isAvailable()) {
                mAutomaticGainControl = AutomaticGainControl
                    .create(mAudioRecord!!.audioSessionId)
                val resultCode = mAutomaticGainControl!!.setEnabled(true)
                if (AudioEffect.SUCCESS == resultCode) {
                    Logger.d(TAG, "agc-->success")
                }
            }
            if (Utils.debugCamera) {
                Logger.i(TAG, "initAudioRecord success")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "initAudioRecord failed, err = ${e.localizedMessage}", e)
        }
    }

    override fun startRecording() {
        try {
            mAudioRecord?.startRecording()
            if (Utils.debugCamera) {
                Logger.i(TAG, "startRecording success-->${mAudioRecord?.recordingState}")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "startRecording failed, err = ${e.localizedMessage}", e)
        }
    }

    override fun stopRecording() {
        try {
            mAudioRecord?.stop()
            if (Utils.debugCamera) {
                Logger.i(TAG, "stopRecording success")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "startRecording failed, err = ${e.localizedMessage}", e)
        }
    }

    override fun releaseAudioRecord() {
        try {
            mAcousticEchoCanceler?.release()
            mAutomaticGainControl?.release()
            mNoiseSuppressor?.release()
            mAudioRecord?.release()
            mAudioRecord = null
            if (Utils.debugCamera) {
                Logger.i(TAG, "releaseAudioRecord success.")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "releaseAudioRecord failed, err = ${e.localizedMessage}", e)
        }
    }

    override fun read(): RawData? {
        return if (!isRecording()) {
            null
        } else {
            val data = ByteArray(mBufferSize)
            val readBytes = mAudioRecord?.read(data, 0, mBufferSize) ?: 0
            RawData(data, readBytes)
        }
    }

    override fun isRecording(): Boolean =
        mAudioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING

    override fun getSampleRate(): Int {
        return mConfig.sampleRate
    }

    override fun getAudioFormat(): Int {
        return mConfig.encodingConfig
    }

    override fun getChannelCount(): Int {
        return mConfig.channelCount
    }

    override fun getChannelConfig(): Int {
        return mConfig.channelConfig
    }

    companion object {
        private const val TAG = "AudioSystem"
        private const val AUDIO_RECORD_SOURCE = MediaRecorder.AudioSource.MIC
    }
}