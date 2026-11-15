package top.nekora.clone.ui

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.nekora.clone.state.ItemText
import top.nekora.clone.state.Panel
import top.nekora.clone.state.PanelRow
import top.nekora.clone.state.PanelSpec
import top.nekora.clone.state.PanelState
import top.nekora.clone.state.Prefs
import top.nekora.clone.state.RowKind
import top.nekora.clone.state.SettingsGroup
import top.nekora.clone.state.SettingsItem

/** 宿主内弹出的设置面板 */
object SettingsPanel {

    fun build(host: Context, pkg: String, onClose: () -> Unit): View {
        val view = ComposeView(host)
        view.setContent { Root(pkg, onClose) }
        return view
    }
}

private val BG = Color(0xFFF2F3F5)
private val CARD = Color.White
private val T1 = Color(0xFF1A1A1A)
private val T2 = Color(0xFF8A8A8E)
private val ACC = Color(0xFF2B6BE4)

private sealed interface Page {
    data object Home : Page
    data class Group(val g: SettingsGroup) : Page
    data class Detail(val panel: Panel) : Page
}

@Composable
private fun Root(pkg: String, onClose: () -> Unit) {
    val state = remember { PanelState(pkg) }
    var page by remember { mutableStateOf<Page>(Page.Home) }

    Surface(Modifier.fillMaxSize(), color = BG) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            TopBar(
                title = when (val p = page) {
                    is Page.Home -> "模块设置"
                    is Page.Group -> p.g.title
                    is Page.Detail -> p.panel.title
                },
                sub = when (val p = page) {
                    is Page.Home -> hostLabel(pkg)
                    is Page.Group -> hostLabel(pkg)
                    is Page.Detail -> PanelSpec.subtitleOf(p.panel)
                },
                onBack = {
                    when (page) {
                        is Page.Home -> { onClose(); Page.Home }
                        is Page.Group -> Page.Home
                        is Page.Detail -> Page.Home
                    }.let { page = it }
                }
            )
            Spacer(Modifier.height(14.dp))

            when (val p = page) {
                is Page.Home -> HomeBody(state, pkg) { page = it }
                is Page.Group -> GroupBody(state, p.g, pkg) { page = Page.Detail(it) }
                is Page.Detail -> PanelBody(state, p.panel)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "部分功能需完整重启宿主后生效", fontSize = 12.sp, color = T2,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun TopBar(title: String, sub: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clickable { onBack() }, contentAlignment = Alignment.Center) {
            Text("←", fontSize = 22.sp, color = T1)
        }
        Spacer(Modifier.width(6.dp))
        Column {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = T1)
            Text(sub, fontSize = 12.sp, color = T2)
        }
    }
}

@Composable
private fun HomeBody(state: PanelState, pkg: String, go: (Page) -> Unit) {
    AccountCard(state)
    Spacer(Modifier.height(16.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("常用设置", fontSize = 13.sp, color = T2)
        Text("编辑", fontSize = 13.sp, color = ACC)
    }
    Spacer(Modifier.height(8.dp))

    Card {
        val favs = state.favorites(pkg)
        if (favs.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                Text("点「编辑」把常用开关放到这里", fontSize = 13.sp, color = T2)
            }
        } else {
            favs.forEachIndexed { i, it ->
                if (i > 0) Div()
                SwitchRow(ItemText.title(it), ItemText.desc(it), state.enabled(it)) { v ->
                    state.setEnabled(it, v)
                }
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    Text("功能设置", fontSize = 13.sp, color = T2)
    Spacer(Modifier.height(8.dp))

    Card {
        ItemText.orderedGroups.forEachIndexed { gi, g ->
            val items = ItemText.itemsOf(g, pkg)
            if (items.isEmpty()) return@forEachIndexed
            if (gi > 0 && ItemText.orderedGroups.take(gi).any { ItemText.itemsOf(it, pkg).isNotEmpty() }) Div()
            val on = items.count { state.enabled(it) }
            GroupRow(g, on, items.size) { go(Page.Group(g)) }
        }
    }
}

@Composable
private fun GroupBody(state: PanelState, g: SettingsGroup, pkg: String, go: (Panel) -> Unit) {
    Card {
        val items = ItemText.itemsOf(g, pkg)
        items.forEachIndexed { i, it ->
            if (i > 0) Div()
            val panel = Panel.of(it)
            if (panel != null) {
                // 有二级面板
                Row2(ItemText.title(it), ItemText.summary(it, state.enabled(it))) { go(panel) }
            } else {
                SwitchRow(ItemText.title(it), ItemText.desc(it), state.enabled(it)) { v ->
                    state.setEnabled(it, v)
                }
            }
        }
    }
}

/** 二级面板：按 PanelSpec 渲染真实控件 */
@Composable
private fun PanelBody(state: PanelState, panel: Panel) {
    // 先放总开关（如果有）
    val rows = PanelSpec.rowsOf(panel)
    val masterKey = state.masterKeyOf(panel)

    if (masterKey != null) {
        Card {
            SwitchRow(
                PanelSpec.rowsOf(panel).firstOrNull { it.key == masterKey }?.title ?: "启用",
                "关闭后本页所有设置不生效",
                Prefs.getBool(masterKey)
            ) { v -> Prefs.setBool(masterKey, v) }
        }
        Spacer(Modifier.height(10.dp))
    }

    Card {
        var first = true
        rows.forEach { row ->
            if (row.key == masterKey) return@forEach
            if (!first) Div()
            first = false
            WhenRow(row)
        }
    }
}

@Composable
private fun WhenRow(row: PanelRow) {
    when (row.kind) {
        RowKind.SWITCH -> SwitchRow(row.title, row.desc, Prefs.getBool(row.key, row.def as? Boolean ?: false)) { v ->
            Prefs.setBool(row.key, v)
        }
        RowKind.INT_FIELD -> IntRow(row)
        RowKind.TEXT_FIELD -> TextRow(row)
        RowKind.MULTI_TEXT -> MultiRow(row)
        RowKind.SLIDER -> SliderRow(row)
        RowKind.CHOICE -> ChoiceRow(row)
        RowKind.IMAGE -> ImageRow(row)
        RowKind.INFO -> InfoRow(row)
    }
}

@Composable
private fun IntRow(row: PanelRow) {
    var v by remember { mutableStateOf(Prefs.getInt(row.key, row.def as? Int ?: 0).toString()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(row.title, fontSize = 16.sp, color = T1)
        if (row.hint.isNotEmpty()) Text(row.hint, fontSize = 11.sp, color = T2)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = v,
            onValueChange = { s ->
                val digits = s.filter { it.isDigit() }.take(9)
                v = digits
                Prefs.setInt(row.key, digits.toIntOrNull() ?: 0)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TextRow(row: PanelRow) {
    var v by remember { mutableStateOf(Prefs.getString(row.key, row.def as? String ?: "")) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(row.title, fontSize = 16.sp, color = T1)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = v, onValueChange = { v = it; Prefs.setString(row.key, it) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MultiRow(row: PanelRow) {
    var v by remember { mutableStateOf(Prefs.getString(row.key)) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(row.title, fontSize = 16.sp, color = T1)
        Spacer(Modifier.height(3.dp))
        Text(row.desc, fontSize = 11.sp, color = T2)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = v,
            onValueChange = { v = it; Prefs.setString(row.key, it) },
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().height(110.dp)
        )
    }
}

@Composable
private fun SliderRow(row: PanelRow) {
    var v by remember { mutableStateOf(Prefs.getInt(row.key, row.def as? Int ?: 100).toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(row.title, fontSize = 16.sp, color = T1)
            Text("${v.toInt()}%", fontSize = 13.sp, color = ACC)
        }
        if (row.desc.isNotEmpty()) Text(row.desc, fontSize = 11.sp, color = T2)
        Slider(
            value = v,
            onValueChange = { v = it; Prefs.setInt(row.key, it.toInt()) },
            valueRange = 0f..100f
        )
    }
}

@Composable
private fun ChoiceRow(row: PanelRow) {
    val cur = Prefs.getInt(row.key, row.def as? Int ?: 0)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(row.title, fontSize = 16.sp, color = T1)
        if (row.desc.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(row.desc, fontSize = 11.sp, color = T2)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.choices.forEach { (label, value) ->
                val on = cur == value
                Box(
                    Modifier
                        .background(
                            if (on) ACC else Color(0xFFF2F2F5),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { Prefs.setInt(row.key, value) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(label, fontSize = 12.sp, color = if (on) Color.White else Color(0xFF4A4A4E))
                }
            }
        }
    }
}

@Composable
private fun ImageRow(row: PanelRow) {
    val path = Prefs.getString(row.key)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(row.title, fontSize = 16.sp, color = T1)
        Spacer(Modifier.height(3.dp))
        Text(
            if (path.isEmpty()) "尚未选择图片，选好前仍用纯色背景" else row.desc,
            fontSize = 11.sp, color = T2
        )
        if (path.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(path.substringAfterLast('/'), fontSize = 11.sp, color = ACC)
        }
    }
}

@Composable
private fun InfoRow(row: PanelRow) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(row.title, fontSize = 12.sp, color = T2)
        if (row.desc.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(row.desc, fontSize = 11.sp, color = T2, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun AccountCard(state: PanelState) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(Color(0xFF1E3A8A), CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.userName.ifBlank { "等待获取" },
                            fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = T1
                        )
                        Spacer(Modifier.width(8.dp))
                        Badge("普通用户")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("快手号：${state.kwaiId.ifBlank { "等待获取" }}", fontSize = 12.sp, color = T2)
                }
            }
            Spacer(Modifier.height(14.dp)); Div(); Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row {
                    Text("用户 ID：", fontSize = 13.sp, color = T2)
                    Text(state.userId.ifBlank { "等待获取" }, fontSize = 13.sp, color = T1)
                }
                Text("刷新", fontSize = 13.sp, color = ACC,
                    modifier = Modifier.clickable { state.refreshUser() })
            }
        }
    }
}

@Composable
private fun Badge(t: String) {
    Box(Modifier.background(Color(0xFFE8EFFC), RoundedCornerShape(6.dp))
        .padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(t, fontSize = 11.sp, color = ACC)
    }
}

@Composable
private fun GroupRow(g: SettingsGroup, on: Int, total: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(g.title, fontSize = 16.sp, color = T1)
            Spacer(Modifier.height(3.dp))
            Text(g.subtitle, fontSize = 12.sp, color = T2)
        }
        if (on > 0) {
            Text("$on/$total", fontSize = 12.sp, color = ACC)
            Spacer(Modifier.width(6.dp))
        }
        Text("›", fontSize = 20.sp, color = Color(0xFFC0C0C4))
    }
}

@Composable
private fun Row2(title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = T1)
            Spacer(Modifier.height(3.dp))
            Text(summary, fontSize = 12.sp, color = T2)
        }
        Text("›", fontSize = 20.sp, color = Color(0xFFC0C0C4))
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = T1)
            if (desc.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(desc, fontSize = 12.sp, color = T2)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ACC))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().background(CARD, RoundedCornerShape(14.dp))) {
        Column { content() }
    }
}

@Composable
private fun Div() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(Color(0xFFF0F0F2)))
}

private fun hostLabel(pkg: String) = when (pkg) {
    "com.kuaishou.nebula" -> "快手极速版"
    "com.smile.gifmaker" -> "快手标准版"
    else -> pkg
}
