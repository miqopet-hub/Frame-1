package app.frame.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.frame.domain.BorderSettings
import app.frame.domain.BorderStyle
import app.frame.domain.DoubleLineSettings
import app.frame.domain.LineColorMode
import app.frame.domain.Side
import app.frame.domain.withGlobal
import app.frame.domain.withLinked
import app.frame.domain.withSide
import app.frame.domain.withStyle
import app.frame.imaging.ExportFormat
import app.frame.imaging.ExportResult
import app.frame.imaging.FrameExporter
import app.frame.imaging.ImageLoader
import app.frame.imaging.LoadedImage
import app.frame.imaging.PaletteExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A point the eyedropper sampled, in coordinates of the ORIGINAL photograph. */
data class EyedropperSample(val originalX: Int, val originalY: Int, val u: Float, val v: Float, val color: Int)

data class FrameUiState(
    val image: LoadedImage? = null,
    val loading: Boolean = false,
    val exporting: Boolean = false,
    val settings: BorderSettings = BorderSettings(),
    val borderColor: Int = 0xFFFFFFFF.toInt(),
    val palette: List<Int> = emptyList(),
    val eyedropperActive: Boolean = false,
    val sample: EyedropperSample? = null,
    val format: ExportFormat = ExportFormat.JPEG,
)

class FrameViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(FrameUiState())
    val state: StateFlow<FrameUiState> = _state

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var loadJob: Job? = null

    // ---- import -----------------------------------------------------------------------

    fun loadImage(uri: Uri) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val image = ImageLoader.load(getApplication(), uri)
                val palette = withContext(Dispatchers.Default) { paletteFor(image.preview) }
                _state.update {
                    it.copy(
                        image = image,
                        palette = palette,
                        loading = false,
                        eyedropperActive = false,
                        sample = null,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                _state.update { it.copy(loading = false) }
                _messages.trySend("That photo is too large to open on this device.")
            } catch (e: Exception) {
                _state.update { it.copy(loading = false) }
                _messages.trySend("Couldn't open that image: ${e.message ?: "unknown error"}")
            }
        }
    }

    private fun paletteFor(preview: Bitmap): List<Int> {
        val maxSide = 160
        val scale = maxSide.toFloat() / maxOf(preview.width, preview.height)
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                preview,
                maxOf(1, (preview.width * scale).toInt()),
                maxOf(1, (preview.height * scale).toInt()),
                true,
            )
        } else preview
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        if (small !== preview) small.recycle()
        return PaletteExtractor.extract(pixels, 10)
    }

    // ---- border settings --------------------------------------------------------------

    private fun editSettings(f: (BorderSettings) -> BorderSettings) = _state.update { it.copy(settings = f(it.settings)) }

    fun setStyle(style: BorderStyle) = editSettings { it.withStyle(style) }
    fun setGlobalWidth(percent: Float) = editSettings { it.withGlobal(percent) }
    fun setLinked(linked: Boolean) = editSettings { it.withLinked(linked) }
    fun setSide(side: Side, percent: Float) = editSettings { it.withSide(side, percent) }
    fun setBottomWeight(weight: Float) = editSettings { it.copy(bottomWeight = weight) }
    fun editDouble(f: (DoubleLineSettings) -> DoubleLineSettings) =
        editSettings { it.copy(doubleLines = f(it.doubleLines)) }

    fun setLineColorMode(mode: LineColorMode) = editDouble { it.copy(colorMode = mode) }

    // ---- colour -----------------------------------------------------------------------

    fun setBorderColor(argb: Int) = _state.update { it.copy(borderColor = argb or (0xFF shl 24)) }

    fun setEyedropper(active: Boolean) = _state.update { it.copy(eyedropperActive = active) }

    /** [u],[v] are 0..1 positions inside the photograph (not the canvas). */
    fun sampleAt(u: Float, v: Float) {
        val image = _state.value.image ?: return
        val preview = image.preview
        val px = (u * preview.width).toInt().coerceIn(0, preview.width - 1)
        val py = (v * preview.height).toInt().coerceIn(0, preview.height - 1)
        val color = preview.getPixel(px, py) or (0xFF shl 24)
        val ox = (u * image.width).toInt().coerceIn(0, image.width - 1)
        val oy = (v * image.height).toInt().coerceIn(0, image.height - 1)
        _state.update { it.copy(borderColor = color, sample = EyedropperSample(ox, oy, u, v, color)) }
    }

    // ---- export -----------------------------------------------------------------------

    fun setFormat(format: ExportFormat) = _state.update { it.copy(format = format) }

    fun postMessage(text: String) {
        _messages.trySend(text)
    }

    fun export() {
        val s = _state.value
        val image = s.image ?: return
        if (s.exporting) return
        _state.update { it.copy(exporting = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                FrameExporter.export(getApplication(), image, s.settings, s.borderColor, s.format)
            }
            _state.update { it.copy(exporting = false) }
            when (result) {
                is ExportResult.Success -> {
                    val note = if (result.reducedForMemory) " (reduced to fit device memory)" else ""
                    _messages.trySend(
                        "Saved ${result.width} × ${result.height} px to Pictures/${FrameExporter.FOLDER}$note",
                    )
                }
                is ExportResult.Failure -> _messages.trySend("Export failed: ${result.message}")
            }
        }
    }
}
