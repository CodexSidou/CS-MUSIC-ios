package com.rst.player.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.rst.player.data.db.entity.SongMoodEntity
import com.rst.player.data.model.Song
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Decodes the first ~20 seconds of a song and reads its actual audio:
 * tempo (BPM from the beat envelope), energy (loudness), brightness (spectral
 * centroid of the instrumentation) and bass weight. This is the "AI" behind
 * mood-aware mixes — no cloud, runs fully on the device.
 */
object AudioMoodAnalyzer {

    private const val TARGET_RATE = 11025
    private const val MAX_SECONDS = 20f
    private const val FFT_SIZE = 1024
    private const val TIMEOUT_US = 10_000L

    fun analyze(context: Context, song: Song): SongMoodEntity? {
        val samples = decodeMono(context, song) ?: return null
        if (samples.size < FFT_SIZE * 4) return null
        val features = extractFeatures(samples)
        return SongMoodEntity(
            songId = song.id.toString(),
            bpm = features.bpm,
            energy = features.energy,
            brightness = features.brightness,
            bassiness = features.bassiness,
            darkness = features.darkness,
            spectralFlux = features.spectralFlux,
            rhythmicDensity = features.rhythmicDensity,
            dynamicRange = features.dynamicRange,
            valence = features.valence
        )
    }

    private data class Features(
        val bpm: Float,
        val energy: Float,
        val brightness: Float,
        val bassiness: Float,
        val darkness: Float,
        val spectralFlux: Float,
        val rhythmicDensity: Float,
        val dynamicRange: Float,
        val valence: Float
    )

    private fun decodeMono(context: Context, song: Song): FloatArray? {
        val extractor = MediaExtractor()
        try {
            runCatching { extractor.setDataSource(context, song.uri, null) }
                .getOrElse { return null }
        } catch (t: Throwable) {
            return null
        }

        var track = -1
        var fmt: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) {
                track = i
                fmt = f
                break
            }
        }
        if (track < 0 || fmt == null) {
            extractor.release()
            return null
        }

        val mime = fmt.getString(MediaFormat.KEY_MIME)
        if (mime == null) {
            extractor.release()
            return null
        }
        val codec = runCatching { MediaCodec.createDecoderByType(mime) }.getOrNull()
            ?: run {
                extractor.release()
                return null
            }

        try {
            runCatching { codec.configure(fmt, null, null, 0) }.getOrElse {
                runCatching { codec.release() }
                extractor.release()
                return null
            }
            codec.start()
            extractor.selectTrack(track)

            var channelCount = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).takeIf { it > 0 } ?: 2
            var sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).takeIf { it > 0 } ?: 44100
            val downSample = maxOf(1, sampleRate / TARGET_RATE)

            val targetFrames = (MAX_SECONDS * TARGET_RATE).toInt()
            val out = FloatArray(targetFrames)
            var outIndex = 0
            val bufInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone && outIndex < targetFrames) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            inBuf.clear()
                            val size = extractor.readSampleData(inBuf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufInfo, TIMEOUT_US)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFmt = codec.outputFormat
                        val cc = newFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (cc > 0) channelCount = cc
                        val sr = newFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (sr > 0) sampleRate = sr
                    }
                    outIdx >= 0 -> {
                        val buf = codec.getOutputBuffer(outIdx)
                        if (buf != null) {
                            buf.order(ByteOrder.nativeOrder())
                            var bytePos = 0
                            while (bytePos + 2 * channelCount <= bufInfo.size && outIndex < targetFrames) {
                                val frame = bytePos / (2 * channelCount)
                                if (frame % downSample == 0) {
                                    var acc = 0L
                                    for (ch in 0 until channelCount) {
                                        acc += buf.getShort(bytePos + 2 * ch).toLong()
                                    }
                                    out[outIndex++] = (acc.toFloat() / channelCount) / 32768f
                                }
                                bytePos += 2 * channelCount
                            }
                            val end = bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(outIdx, false)
                            if (end) outputDone = true
                        }
                    }
                }
            }
            return if (outIndex >= FFT_SIZE * 4) out.copyOf(outIndex) else null
        } catch (t: Throwable) {
            return null
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            extractor.release()
        }
    }

    private fun extractFeatures(s: FloatArray): Features {
        val n = s.size

        // --- Energy: RMS loudness mapped from ~ -55 dB..0 dB to 0..1 ---
        var sq = 0.0
        for (i in 0 until n) sq += s[i].toDouble() * s[i]
        val rms = sqrt(sq / n)
        val db = 20.0 * log10(maxOf(rms, 1e-6))
        val energy = ((db + 55.0) / 55.0).coerceIn(0.0, 1.0).toFloat()

        // --- Spectral centroid (brightness) + bass weight via FFT windows ---
        val fft = FloatArray(FFT_SIZE * 2)
        var centroidSum = 0.0
        var centroidW = 0.0
        var bassSum = 0.0
        var midSum = 0.0
        var highSum = 0.0
        var totalMag = 0.0
        val freqPerBin = TARGET_RATE.toDouble() / FFT_SIZE
        var windows = 0
        var pos = 0
        // Track spectral flux (onset detection)
        var prevMagnitudes = FloatArray(FFT_SIZE / 2)
        var fluxSum = 0.0
        var fluxCount = 0
        while (pos + FFT_SIZE <= n && windows < 50) {
            for (i in 0 until FFT_SIZE) {
                fft[2 * i] = s[pos + i]
                fft[2 * i + 1] = 0f
            }
            rfft(fft, FFT_SIZE)
            var windowFlux = 0.0
            for (bin in 1 until FFT_SIZE / 2) {
                val mag = hypot(fft[2 * bin].toDouble(), fft[2 * bin + 1].toDouble()).toFloat()
                val freq = bin * freqPerBin
                centroidSum += freq * mag
                centroidW += mag
                totalMag += mag
                if (freq <= 260.0) bassSum += mag
                else if (freq <= 2000.0) midSum += mag
                else highSum += mag
                // Spectral flux: sum of positive differences between frames
                val diff = mag - prevMagnitudes[bin]
                if (diff > 0) windowFlux += diff.toDouble()
                prevMagnitudes[bin] = mag
            }
            fluxSum += windowFlux
            fluxCount++
            windows++
            pos += FFT_SIZE / 2
        }
        val brightness = if (centroidW > 0) {
            (centroidSum / centroidW / (TARGET_RATE / 2.0)).toFloat()
        } else 0.3f
        val bassiness = if (totalMag > 0) (bassSum / totalMag).toFloat() else 0.3f

        // Spectral flux: normalised onset strength (0 = drone, 1 = sharp transients)
        val spectralFlux = if (fluxCount > 0) {
            ((fluxSum / fluxCount) / (totalMag / windows.coerceAtLeast(1)) * 4.0).coerceIn(0.0, 1.0).toFloat()
        } else 0.3f

        // --- Mid/high ratio for valence estimation ---
        val midRatio = if (totalMag > 0) (midSum / totalMag).toFloat() else 0.3f
        val highRatio = if (totalMag > 0) (highSum / totalMag).toFloat() else 0.1f

        // --- Dynamic range: difference between loudest and quietest windows ---
        val windowEnergies = ArrayList<Float>()
        var wp = 0
        val wSize = (TARGET_RATE * 0.1f).toInt().coerceAtLeast(FFT_SIZE)
        while (wp + wSize <= n) {
            var wSq = 0.0
            for (i in wp until wp + wSize) wSq += s[i].toDouble() * s[i]
            windowEnergies.add(sqrt(wSq / wSize).toFloat())
            wp += wSize / 2
        }
        val dynamicRange: Float = if (windowEnergies.size >= 4) {
            val sorted = windowEnergies.sorted()
            val p10 = sorted[(sorted.size * 0.1f).toInt().coerceIn(0, sorted.size - 1)]
            val p90 = sorted[(sorted.size * 0.9f).toInt().coerceIn(0, sorted.size - 1)]
            val dr = if (p10 > 0) (p90 / p10).coerceIn(1f, 20f) / 20f else 0.3f
            dr
        } else 0.3f

        // --- Tempo: autocorrelation of the ~40 ms energy envelope ---
        val hop = (TARGET_RATE * 0.04f).toInt()
        val envelope = ArrayList<Float>()
        var e = 0
        while (e < n) {
            val end = minOf(e + hop, n)
            var acc = 0.0
            for (i in e until end) acc += s[i].toDouble() * s[i]
            envelope.add(acc.toFloat())
            e += hop
        }
        val bpm = estimateBpm(envelope, hop.toFloat() / TARGET_RATE)

        // --- Rhythmic density: fraction of onset-heavy frames ---
        val onsetThreshold = if (fluxCount > 0) (fluxSum / fluxCount) * 0.5 else 0.0
        var onsetFrames = 0
        wp = 0
        var wFlux = 0.0
        var wCount = 0
        prevMagnitudes = FloatArray(FFT_SIZE / 2)
        while (wp + FFT_SIZE <= n && wCount < fluxCount) {
            for (i in 0 until FFT_SIZE) {
                fft[2 * i] = s[wp + i]
                fft[2 * i + 1] = 0f
            }
            rfft(fft, FFT_SIZE)
            var frameFlux = 0.0
            for (bin in 1 until FFT_SIZE / 2) {
                val mag = hypot(fft[2 * bin].toDouble(), fft[2 * bin + 1].toDouble()).toFloat()
                val diff = mag - prevMagnitudes[bin]
                if (diff > 0) frameFlux += diff.toDouble()
                prevMagnitudes[bin] = mag
            }
            if (frameFlux > onsetThreshold) onsetFrames++
            wFlux += frameFlux
            wCount++
            wp += FFT_SIZE / 2
        }
        val rhythmicDensity = if (wCount > 0) (onsetFrames.toFloat() / wCount).coerceIn(0f, 1f) else 0.3f

        // Darkness: dim + slow + quiet = night. Baked from what the music IS.
        val darkness = ((1f - brightness) * 0.6f + (1f - energy) * 0.4f).coerceIn(0f, 1f)

        // Valence (perceived positivity): bright + mid-heavy + energetic = happy;
        // dark + bass-heavy + quiet = sad.  Tuned via multiple spectral features.
        val rawValence = (brightness * 0.35f + midRatio * 0.25f + energy * 0.2f + (1f - bassiness) * 0.2f)
        val valence = rawValence.coerceIn(0f, 1f)

        return Features(bpm, energy, brightness, bassiness, darkness, spectralFlux, rhythmicDensity, dynamicRange, valence)
    }

    private fun estimateBpm(envelope: List<Float>, hopSeconds: Float): Float {
        if (envelope.size < 20) return 120f
        val fps = 1f / hopSeconds
        val minLag = (fps * 60f / 180f).toInt().coerceAtLeast(2)
        val maxLag = (fps * 60f / 60f).toInt().coerceAtMost(envelope.size - 2)
        if (maxLag <= minLag) return 120f
        var bestLag = minLag
        var bestScore = -1.0
        for (lag in minLag..maxLag) {
            var num = 0.0
            var denA = 0.0
            var denB = 0.0
            var count = 0
            for (i in 0 until envelope.size - lag) {
                num += envelope[i].toDouble() * envelope[i + lag]
                denA += envelope[i].toDouble() * envelope[i]
                denB += envelope[i + lag].toDouble() * envelope[i + lag]
                count++
            }
            if (count == 0) continue
            val denom = sqrt(denA * denB)
            val score = if (denom > 0) num / denom else 0.0
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        val raw = 60f * fps / bestLag
        var bpm = raw
        while (bpm < 70f) bpm *= 2f
        while (bpm > 170f) bpm /= 2f
        return bpm.roundToInt().toFloat()
    }

    /**
     * In-place real FFT (radix-2). Input: complex values packed re/im.
     * After the call, bin k magnitude = hypot(reIm[2k], reIm[2k+1]).
     */
    private fun rfft(reIm: FloatArray, size: Int) {
        val half = size / 2
        var j = 0
        for (i in 1 until half) {
            var bit = half shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val a = 2 * i
                val b = 2 * j
                val tr0 = reIm[a]
                val tr1 = reIm[a + 1]
                reIm[a] = reIm[b]
                reIm[a + 1] = reIm[b + 1]
                reIm[b] = tr0
                reIm[b + 1] = tr1
            }
        }
        var len = 2
        while (len <= half) {
            val ang = -2.0 * PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var i = 0
            while (i < half) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val base = 2 * (i + k)
                    val uRe = reIm[base]
                    val uIm = reIm[base + 1]
                    val vIdx = base + len
                    val vRe = reIm[vIdx] * curRe - reIm[vIdx + 1] * curIm
                    val vIm = reIm[vIdx] * curIm + reIm[vIdx + 1] * curRe
                    reIm[base] = uRe + vRe
                    reIm[base + 1] = uIm + vIm
                    reIm[vIdx] = uRe - vRe
                    reIm[vIdx + 1] = uIm - vIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
