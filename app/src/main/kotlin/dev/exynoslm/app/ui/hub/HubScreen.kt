package dev.exynoslm.app.ui.hub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import dev.exynoslm.app.R
import dev.exynoslm.app.download.DownloadWorker
import dev.exynoslm.app.ui.components.LxBadge
import dev.exynoslm.app.ui.components.LxButton
import dev.exynoslm.app.ui.components.LxButtonKind
import dev.exynoslm.app.ui.components.LxCard
import dev.exynoslm.app.ui.components.LxEmptyState
import dev.exynoslm.app.ui.components.LxErrorState
import dev.exynoslm.app.ui.components.LxFilterChip
import dev.exynoslm.app.ui.components.LxFlow
import dev.exynoslm.app.ui.components.LxIconButton
import dev.exynoslm.app.ui.components.LxNotice
import dev.exynoslm.app.ui.components.LxOfflineNotice
import dev.exynoslm.app.ui.components.LxProgressBar
import dev.exynoslm.app.ui.components.LxScreenHeader
import dev.exynoslm.app.ui.components.LxSearchField
import dev.exynoslm.app.ui.components.LxSectionTitle
import dev.exynoslm.app.ui.components.LxStatTile
import dev.exynoslm.app.ui.components.LxStatusPill
import dev.exynoslm.app.ui.components.LxTone
import dev.exynoslm.app.ui.components.color
import dev.exynoslm.app.ui.components.compactCount
import dev.exynoslm.app.ui.mb
import dev.exynoslm.app.ui.theme.LxMono
import dev.exynoslm.app.ui.theme.Lx
import dev.exynoslm.model.CompatBadge
import dev.exynoslm.model.LicenseRisk
import dev.exynoslm.model.ModelCard
import dev.exynoslm.model.ModelFile
import dev.exynoslm.model.ModelFormat
import dev.exynoslm.model.ModelTask

@Composable
private fun TaskFilter.text(): String = stringResource(
    when (this) {
        TaskFilter.ALL -> R.string.hub_filter_any
        TaskFilter.CHAT -> R.string.hub_task_chat
        TaskFilter.EMBED -> R.string.hub_task_embed
        TaskFilter.ASR -> R.string.hub_task_asr
        TaskFilter.CLS -> R.string.hub_task_cls
        TaskFilter.DET -> R.string.hub_task_det
        TaskFilter.OCR -> R.string.hub_task_ocr
        TaskFilter.IMGGEN -> R.string.hub_task_imggen
    },
)

@Composable
private fun LibFilter.text(): String =
    if (this == LibFilter.ANY) stringResource(R.string.hub_filter_any) else name.lowercase()

@Composable
private fun CompatBadge.text(): String = stringResource(
    when (this) {
        CompatBadge.NPU_READY -> R.string.badge_npu_ready
        CompatBadge.GPU -> R.string.badge_gpu
        CompatBadge.CPU -> R.string.badge_cpu
        CompatBadge.NEEDS_CONVERSION -> R.string.badge_needs_conversion
        CompatBadge.TOO_LARGE -> R.string.badge_too_large
        CompatBadge.HEAVY_FOR_MOBILE -> R.string.badge_heavy
    },
)

private fun CompatBadge.tone(): LxTone = when (this) {
    CompatBadge.NPU_READY -> LxTone.Success
    CompatBadge.GPU -> LxTone.Accent
    CompatBadge.CPU -> LxTone.Neutral
    CompatBadge.NEEDS_CONVERSION -> LxTone.Warn
    CompatBadge.TOO_LARGE -> LxTone.Danger
    CompatBadge.HEAVY_FOR_MOBILE -> LxTone.Warn
}

private fun LicenseRisk.tone(): LxTone = when (this) {
    LicenseRisk.PERMISSIVE -> LxTone.Success
    LicenseRisk.ATTRIBUTION -> LxTone.Accent
    LicenseRisk.RESTRICTED -> LxTone.Warn
    LicenseRisk.UNKNOWN -> LxTone.Neutral
}

@Composable
fun HubScreen(vm: HubViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    BackHandler(enabled = ui.detail != null) { vm.closeDetail() }
    val detail = ui.detail
    if (detail != null) {
        HubDetail(detail, vm, downloads)
        return
    }
    Column(Modifier.fillMaxSize()) {
        LxScreenHeader(title = stringResource(R.string.nav_hub), kicker = stringResource(R.string.app_kicker))
        LxSearchField(
            value = ui.query,
            onValueChange = { vm.setQuery(it) },
            placeholder = stringResource(R.string.search_hint),
            onSearch = { vm.search() },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            loading = ui.loading,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TaskFilter.entries.forEach { LxFilterChip(selected = ui.task == it, onClick = { vm.setTask(it) }, label = it.text()) }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibFilter.entries.forEach { LxFilterChip(selected = ui.lib == it, onClick = { vm.setLib(it) }, label = it.text()) }
        }
        if (ui.loading) LxProgressBar(null, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), height = 4.dp)

        val err = ui.error
        if (err != null && ui.results.isEmpty() && !ui.loading) {
            LxErrorState(
                title = stringResource(R.string.hub_error_title),
                message = stringResource(R.string.hub_error_sub),
                details = err,
                detailsLabel = stringResource(R.string.details),
                retryLabel = stringResource(R.string.retry),
                onRetry = { vm.search() },
            )
        } else if (ui.results.isEmpty() && !ui.loading) {
            LxEmptyState(stringResource(R.string.hub_empty_title), stringResource(R.string.hub_empty_sub))
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (err != null) item(key = "err") { LxNotice(err, Modifier.fillMaxWidth(), LxTone.Danger, stringResource(R.string.hub_error_title)) }
                items(ui.results, key = { it.repoId }) { m -> HubModelCard(m, vm) }
                if (ui.next != null) {
                    item(key = "more") {
                        LxButton(
                            stringResource(R.string.hub_load_more), { vm.loadMore() }, Modifier.fillMaxWidth(),
                            kind = LxButtonKind.Secondary, enabled = !ui.loading,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HubModelCard(m: ModelCard, vm: HubViewModel) {
    val c = Lx.colors
    LxCard(Modifier.fillMaxWidth(), onClick = { vm.open(m) }, contentPadding = PaddingValues(14.dp)) {
        Text(
            m.repoId, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight(800), lineHeight = 20.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        LxFlow(Modifier.padding(top = 8.dp)) {
            m.pipelineTag?.let { LxBadge(it, tone = LxTone.Accent) }
            m.libraryName?.let { LxBadge(it, tone = LxTone.Neutral) }
            LxBadge(m.license ?: stringResource(R.string.hub_no_license), tone = vm.licenseRisk(m).tone())
            if (m.gated) LxBadge(stringResource(R.string.hub_gated_short), tone = LxTone.Danger)
            if (m.task == ModelTask.IMAGE_GENERATION) LxBadge(stringResource(R.string.hub_heavy), tone = LxTone.Warn)
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("↓ ${m.downloads.compactCount()}", color = c.ink3, fontSize = 12.sp, fontWeight = FontWeight(750))
            Text("♥ ${m.likes.compactCount()}", color = c.ink3, fontSize = 12.sp, fontWeight = FontWeight(750))
        }
    }
}

@Composable
private fun HubDetail(card: ModelCard, vm: HubViewModel, downloads: Map<String, WorkInfo>) {
    val risk = vm.licenseRisk(card)
    // Only model weight files are offered — we never pull the whole repo.
    val files = card.files.filter { it.format != ModelFormat.UNKNOWN }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp)) {
            LxIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), { vm.closeDetail() }, size = 42.dp)
        }
        LxScreenHeader(
            title = card.repoId.substringAfter('/'),
            kicker = card.author ?: stringResource(R.string.app_kicker),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "stats") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LxStatTile(stringResource(R.string.hub_license), card.license ?: stringResource(R.string.hub_no_license), Modifier.weight(1f), valueColor = risk.tone().color())
                        LxStatTile(stringResource(R.string.hub_downloads), card.downloads.compactCount(), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LxStatTile(stringResource(R.string.hub_author), card.author ?: "—", Modifier.weight(1f))
                        LxStatTile(stringResource(R.string.hub_updated), card.lastModified?.take(10) ?: "—", Modifier.weight(1f))
                    }
                }
            }
            if (card.gated) item(key = "gated") { LxNotice(stringResource(R.string.hub_gated), Modifier.fillMaxWidth(), LxTone.Danger) }
            if (risk != LicenseRisk.PERMISSIVE) item(key = "license") { LxNotice(stringResource(R.string.license_warning), Modifier.fillMaxWidth(), LxTone.Warn) }
            if (card.tags.isNotEmpty()) {
                item(key = "tags") {
                    Column {
                        LxSectionTitle(stringResource(R.string.hub_tags))
                        LxFlow(Modifier.padding(top = 6.dp), gap = 5.dp) { card.tags.take(12).forEach { LxBadge(it, tone = LxTone.Neutral) } }
                    }
                }
            }
            item(key = "filesTitle") { LxSectionTitle(stringResource(R.string.hub_files), Modifier.padding(top = 6.dp)) }
            if (files.isEmpty()) item(key = "nofiles") { LxNotice(stringResource(R.string.hub_no_files), Modifier.fillMaxWidth(), LxTone.Neutral) }
            items(files, key = { it.path }) { f -> HubFileCard(card, f, vm, downloads[DownloadWorker.workName(card.repoId, f.path)]) }
        }
    }
}

@Composable
private fun HubFileCard(card: ModelCard, f: ModelFile, vm: HubViewModel, wi: WorkInfo?) {
    val c = Lx.colors
    LxCard(Modifier.fillMaxWidth()) {
        Text(f.path, color = c.ink, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight(700), fontFamily = LxMono)
        Text(
            "${f.sizeBytes?.mb() ?: "? MB"} · ${f.format} · ${f.quantization}",
            Modifier.padding(top = 4.dp), color = c.ink3, fontSize = 12.sp, fontWeight = FontWeight(650),
        )
        LxFlow(Modifier.padding(top = 8.dp), gap = 5.dp) {
            vm.badges(card, f).forEach { LxBadge(it.text(), tone = it.tone()) }
        }
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (wi?.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    val pct = wi.progress.getInt(DownloadWorker.K_PCT, -1)
                    LxProgressBar(if (pct >= 0) pct / 100f else null)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (pct >= 0) "$pct%" else "…", Modifier.weight(1f),
                            color = c.ink2, fontSize = 13.sp, fontWeight = FontWeight(800),
                        )
                        LxButton(
                            stringResource(R.string.hub_pause_cancel), { vm.cancel(card, f) },
                            kind = LxButtonKind.Secondary, icon = Icons.Default.Pause, height = 42.dp,
                        )
                    }
                    if (wi.state == WorkInfo.State.ENQUEUED) {
                        LxOfflineNotice(stringResource(R.string.hub_waiting_network_title), stringResource(R.string.hub_waiting_network))
                    }
                }
                WorkInfo.State.SUCCEEDED -> LxStatusPill(stringResource(R.string.hub_downloaded), c.mint, blink = false)
                WorkInfo.State.FAILED -> {
                    LxNotice(
                        wi.outputData.getString(DownloadWorker.K_ERROR) ?: "—",
                        Modifier.fillMaxWidth(), LxTone.Danger, stringResource(R.string.hub_download_failed),
                    )
                    LxButton(stringResource(R.string.retry), { vm.download(card, f) }, Modifier.fillMaxWidth(), icon = Icons.Default.Refresh, height = 44.dp)
                }
                else -> LxButton(stringResource(R.string.download), { vm.download(card, f) }, Modifier.fillMaxWidth(), icon = Icons.Default.Download, height = 44.dp)
            }
        }
    }
}
