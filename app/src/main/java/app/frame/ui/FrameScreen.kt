package app.frame.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.frame.domain.BorderStyle
import app.frame.domain.ColorMath
import app.frame.domain.FrameLayout
import app.frame.domain.LayoutCalculator
import app.frame.domain.Limits
import app.frame.domain.LineColorMode
import app.frame.domain.Side
import app.frame.domain.get
import app.frame.domain.globalValue
import app.frame.imaging.ExportFormat
import app.frame.ui.components.ColorChip
import app.frame.ui.components.FramePreview
import app.frame.ui.components.HexField
import app.frame.ui.components.SectionHint
import app.frame.ui.components.SegmentedChips
import app.frame.ui.components.Swatch
import app.frame.ui.components.ValueSlider
import app.frame.ui.components.widthText
import app.frame.ui.theme.FramePalette
import kotlin.math.min

private enum class Tab(val label: String) { STYLE("Style"), WIDTH("Width"), COLOR("Color"), SAVE("Save") }

@Composable
fun FrameApp(vm: FrameViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it, duration = SnackbarDuration.Long) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.loadImage(uri)
    }
    fun openPicker() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // Android 8/9 need WRITE_EXTERNAL_STORAGE to save into Pictures/FRAME; 10+ do not.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.export() else vm.postMessage("Storage permission is needed to save on this Android version.")
    }
    fun requestExport() {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else vm.export()
    }

    val image = state.image
    val layout = remember(image, state.settings) {
        image?.let { LayoutCalculator.compute(it.width, it.height, state.settings) }
    }
    val lineColor = ColorMath.resolveLineColor(state.settings.doubleLines.colorMode, state.borderColor)
    var tabIndex by rememberSaveable { mutableStateOf(0) } // -1 = panel collapsed

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
        ) {
            val screenHeight = maxHeight
            val wide = maxWidth > screenHeight * 1.3f

            val preview: @Composable (Modifier) -> Unit = { mod ->
                Box(mod.padding(8.dp), contentAlignment = Alignment.Center) {
                    if (image != null && layout != null) {
                        FramePreview(
                            image = image,
                            layout = layout,
                            borderColor = state.borderColor,
                            lineColor = lineColor,
                            eyedropperActive = state.eyedropperActive,
                            sample = state.sample,
                            onSample = vm::sampleAt,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        EmptyState(onOpen = ::openPicker)
                    }
                    if (state.loading || state.exporting) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            val panel: @Composable (Modifier) -> Unit = { mod ->
                if (tabIndex in Tab.entries.indices && image != null && layout != null) {
                    Column(
                        mod
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (Tab.entries[tabIndex]) {
                            Tab.STYLE -> StylePanel(state, vm)
                            Tab.WIDTH -> WidthPanel(state, vm, layout, min(image.width, image.height))
                            Tab.COLOR -> ColorPanel(state, vm)
                            Tab.SAVE -> SavePanel(state, vm, layout, onExport = ::requestExport)
                        }
                    }
                }
            }

            val tabs: @Composable () -> Unit = {
                TabBar(
                    selected = tabIndex,
                    enabled = image != null,
                    onSelect = { tabIndex = if (it == tabIndex) -1 else it },
                )
            }

            Column(Modifier.fillMaxSize()) {
                TopBar(
                    hasImage = image != null,
                    exporting = state.exporting,
                    onOpen = ::openPicker,
                    onExport = ::requestExport,
                )
                if (wide) {
                    Row(Modifier.weight(1f)) {
                        preview(Modifier.weight(1f).fillMaxHeight())
                        Column(Modifier.width(360.dp).fillMaxHeight()) {
                            panel(Modifier.weight(1f))
                            if (tabIndex !in Tab.entries.indices) Spacer(Modifier.weight(1f))
                            tabs()
                        }
                    }
                } else {
                    preview(Modifier.weight(1f).fillMaxWidth())
                    panel(Modifier.heightIn(max = screenHeight * 0.42f))
                    tabs()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// Chrome
// ---------------------------------------------------------------------------------------

@Composable
private fun TopBar(hasImage: Boolean, exporting: Boolean, onOpen: () -> Unit, onExport: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("FRAME", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onOpen) { Text("Open") }
        Spacer(Modifier.width(4.dp))
        Button(onClick = onExport, enabled = hasImage && !exporting) { Text("Export") }
    }
}

@Composable
private fun TabBar(selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Tab.entries.forEachIndexed { index, tab ->
            val isSelected = index == selected
            Column(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clickable(enabled = enabled) { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        !enabled -> FramePalette.Outline
                        isSelected -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(22.dp)
                        .height(2.dp)
                        .background(if (isSelected && enabled) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onOpen: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Add space around a photograph.\nThe photo itself is never cropped, stretched or changed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Button(onClick = onOpen) { Text("Open photo") }
    }
}

// ---------------------------------------------------------------------------------------
// Panels
// ---------------------------------------------------------------------------------------

private fun styleHint(style: BorderStyle) = when (style) {
    BorderStyle.EVEN -> "Equal border on all four sides."
    BorderStyle.WEIGHTED -> "Deeper bottom border, like a mounted print."
    BorderStyle.ASYMMETRIC -> "Set top, right, bottom and left independently."
    BorderStyle.SQUARE -> "Square canvas. The photo is centred, never cropped."
    BorderStyle.DOUBLE -> "Two fine lines inside the border."
}

@Composable
private fun StylePanel(state: FrameUiState, vm: FrameViewModel) {
    SegmentedChips(
        options = BorderStyle.entries,
        selected = state.settings.style,
        label = { it.label },
        onSelect = vm::setStyle,
    )
    SectionHint(styleHint(state.settings.style))
}

@Composable
private fun WidthPanel(state: FrameUiState, vm: FrameViewModel, layout: FrameLayout, shorter: Int) {
    val s = state.settings

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Link sides", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = s.style != BorderStyle.ASYMMETRIC || s.linked,
            onCheckedChange = vm::setLinked,
        )
    }

    val showFourSliders = s.style == BorderStyle.ASYMMETRIC && !s.linked
    if (showFourSliders) {
        listOf(Side.TOP to "Top", Side.RIGHT to "Right", Side.BOTTOM to "Bottom", Side.LEFT to "Left").forEach { (side, name) ->
            val v = s.sides.get(side)
            ValueSlider(
                label = name,
                value = v,
                max = Limits.MAX_PERCENT,
                valueText = widthText(v, shorter),
                onChange = { vm.setSide(side, it) },
            )
        }
    } else {
        val g = s.globalValue()
        ValueSlider(
            label = if (s.style == BorderStyle.ASYMMETRIC) "All sides (proportional)" else "Border width",
            value = g,
            max = Limits.MAX_PERCENT,
            valueText = widthText(g, shorter),
            onChange = vm::setGlobalWidth,
        )
    }

    if (s.style == BorderStyle.WEIGHTED) {
        ValueSlider(
            label = "Bottom weight",
            value = s.bottomWeight - Limits.MIN_WEIGHT,
            max = Limits.MAX_WEIGHT - Limits.MIN_WEIGHT,
            valueText = "${"%.1f".format(s.bottomWeight)}× the sides",
            onChange = { vm.setBottomWeight(it + Limits.MIN_WEIGHT) },
            power = 1f,
        )
    }

    if (s.style == BorderStyle.DOUBLE) {
        val d = s.doubleLines
        ValueSlider(
            label = "Line thickness",
            value = d.thickness,
            max = Limits.MAX_LINE_THICKNESS,
            valueText = widthText(d.thickness, shorter),
            onChange = { v -> vm.editDouble { it.copy(thickness = v) } },
        )
        ValueSlider(
            label = "Gap between lines",
            value = d.gap,
            max = Limits.MAX_LINE_GAP,
            valueText = widthText(d.gap, shorter),
            onChange = { v -> vm.editDouble { it.copy(gap = v) } },
        )
        ValueSlider(
            label = "Position in border",
            value = d.position,
            max = 1f,
            valueText = when {
                d.position < 0.05f -> "outer edge"
                d.position > 0.95f -> "next to photo"
                else -> "${(d.position * 100).toInt()}%"
            },
            onChange = { v -> vm.editDouble { it.copy(position = v) } },
            power = 1f,
        )
        Text("Line colour", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SegmentedChips(
            options = LineColorMode.entries,
            selected = d.colorMode,
            label = { it.label },
            onSelect = vm::setLineColorMode,
        )
    }

    SectionHint("Output ${layout.canvasWidth} × ${layout.canvasHeight} px · widths are % of the photo's shorter side")
}

@Composable
private fun ColorPanel(state: FrameUiState, vm: FrameViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val active = state.eyedropperActive
        Box(
            Modifier
                .padding(top = 8.dp)
                .height(40.dp)
                .width(96.dp)
                .background(
                    if (active) MaterialTheme.colorScheme.primary else FramePalette.SurfaceHigh,
                    androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                )
                .clickable { vm.setEyedropper(!active) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Eyedropper",
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
        HexField(color = state.borderColor, onColor = vm::setBorderColor, modifier = Modifier.weight(1f))
        ColorChip(state.borderColor, size = 40.dp, modifier = Modifier.padding(top = 8.dp))
    }

    if (state.eyedropperActive) {
        val sample = state.sample
        SectionHint(
            if (sample == null) "Tap or drag on the photo to pick a colour."
            else "Sampled ${ColorMath.toHex(sample.color)} at pixel (${sample.originalX}, ${sample.originalY}) of the original.",
        )
    }

    Text("From this photo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        state.palette.forEach { c ->
            Swatch(
                color = c,
                selected = (c and 0xFFFFFF) == (state.borderColor and 0xFFFFFF),
                onClick = { vm.setBorderColor(c) },
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
            )
        }
    }
}

@Composable
private fun SavePanel(state: FrameUiState, vm: FrameViewModel, layout: FrameLayout, onExport: () -> Unit) {
    SegmentedChips(
        options = ExportFormat.entries,
        selected = state.format,
        label = { it.label },
        onSelect = vm::setFormat,
    )
    val scale = LayoutCalculator.exportScaleFor(layout)
    Text(
        "${layout.canvasWidth} × ${layout.canvasHeight} px · ${"%.1f".format(layout.megapixels)} MP",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (scale < 1f) {
        SectionHint("This is larger than the ${LayoutCalculator.MAX_EXPORT_PIXELS / 1_000_000} MP safe limit, so the whole composition will be scaled down uniformly on export.")
    } else {
        SectionHint(
            if (state.format == ExportFormat.JPEG) "Saved at the original resolution, JPEG quality ${app.frame.imaging.FrameExporter.JPEG_QUALITY}, to Pictures/${app.frame.imaging.FrameExporter.FOLDER}."
            else "Lossless PNG at the original resolution (large files) to Pictures/${app.frame.imaging.FrameExporter.FOLDER}.",
        )
    }
    Button(onClick = onExport, enabled = !state.exporting, modifier = Modifier.fillMaxWidth()) {
        Text(if (state.exporting) "Saving…" else "Export ${state.format.label}")
    }
}
