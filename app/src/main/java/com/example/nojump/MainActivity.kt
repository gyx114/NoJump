package com.example.nojump

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.nojump.ui.theme.NoJumpTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RuleStore.init(this)
        ForegroundWatcher.init(this)
        refreshStates()
        setContent {
            NoJumpTheme {
                NoJumpApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStates()
    }

    private fun refreshStates() {
        state.shizukuOk.value = ShizukuManager.isReady && ShizukuManager.isGranted
        state.a11yOk.value = ForegroundWatcher.hasAccessibilityEnabled()
    }

    companion object {
        val state = State()
    }
}

class State {
    val shizukuOk = mutableStateOf(false)
    val a11yOk = mutableStateOf(false)
}

@Composable
fun NoJumpApp() {
    val context = LocalContext.current
    val source = remember { mutableStateOf(RuleStore.sourceSet) }
    val target = remember { mutableStateOf(RuleStore.targetSet) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    val apps = remember(context, showSystem) { AppList.all(context, showSystem) }
    val running = remember { mutableStateOf(false) }
    val paused = remember { mutableStateOf(RuleStore.paused) }

    val filtered = remember(apps, query) {
        apps.filter {
            query.isBlank() ||
                it.label.contains(query, ignoreCase = true) ||
                it.pkg.contains(query, ignoreCase = true)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text("NoJump", style = MaterialTheme.typography.headlineMedium) }
            item {
                Text(
                    "拦截 App 恶意跳转：在源头应用中禁止跳转到目标应用",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            item { StatusCard(source = source, target = target) }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("一键暂停（打游戏时）", modifier = Modifier.weight(1f))
                    Switch(
                        checked = paused.value,
                        onCheckedChange = {
                            paused.value = it
                            RuleStore.paused = it
                            // 暂停时立即解冻已冻结目标（否则保持禁用，看起来"还在拦截"）
                            if (it) Thread { Freezer.unfreezeAll() }.start()
                        }
                    )
                }
            }

            item {
                Text(
                    "使用前请先开启「无障碍服务」（实时侦测前台）与「Shizuku」（冻结/解冻）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                if (running.value) {
                    OutlinedButton(
                        onClick = { BlockService.stop(context); running.value = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("停止拦截后台服务") }
                } else {
                    Button(
                        onClick = { BlockService.start(context); running.value = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("启动拦截后台服务") }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("无障碍服务") }
                    OutlinedButton(
                        onClick = {
                            val launch = context.packageManager
                                .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            if (launch != null) {
                                runCatching { context.startActivity(launch) }
                            } else {
                                val web = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://shizuku.rikka.app/download/")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(web) }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Shizuku") }
                }
            }

            item { HorizontalDivider() }

            item {
                Text("配置拦截", style = MaterialTheme.typography.titleSmall)
                Text(
                    "触发源头：在这个应用中跳转会被拦截",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "跳转目标：使被冻结的应用无法跳转到此应用",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("列出系统应用（默认只显示常用应用）", modifier = Modifier.weight(1f))
                    Switch(checked = showSystem, onCheckedChange = { showSystem = it })
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("筛选应用") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            items(filtered, key = { it.pkg }) { app ->
                AppRow(
                    app = app,
                    inSource = app.pkg in source.value,
                    inTarget = app.pkg in target.value,
                    onSource = { on ->
                        source.value = if (on) source.value + app.pkg else source.value - app.pkg
                        RuleStore.sourceSet = source.value
                    },
                    onTarget = { on ->
                        target.value = if (on) target.value + app.pkg else target.value - app.pkg
                        RuleStore.targetSet = target.value
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    source: androidx.compose.runtime.MutableState<Set<String>>,
    target: androidx.compose.runtime.MutableState<Set<String>>,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StatusDot("Shizuku 已授权", MainActivity.state.shizukuOk.value)
        StatusDot("无障碍服务（实时侦测前台）", MainActivity.state.a11yOk.value)
        StatusDot("来源 ${source.value.size} 个 / 目标 ${target.value.size} 个", true)
    }
}

@Composable
private fun StatusDot(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(if (ok) Color(0xFF4CAF50) else Color(0xFFF44336), CircleShape)
        )
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.size(6.dp))
}

@Composable
private fun AppRow(
    app: AppList.AppInfo,
    inSource: Boolean,
    inTarget: Boolean,
    onSource: (Boolean) -> Unit,
    onTarget: (Boolean) -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val bitmap = remember(app.icon) {
                runCatching { app.icon?.toBitmap(96, 96) }.getOrNull()
            }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                Text(app.pkg, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Checkbox(checked = inSource, onCheckedChange = onSource)
                Text("作为源头", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Checkbox(checked = inTarget, onCheckedChange = onTarget)
                Text("作为目标", style = MaterialTheme.typography.labelSmall)
            }
        }
        HorizontalDivider()
    }
}