package top.nekora.clone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.nekora.clone.state.AppState

@Composable
fun SettingsScreen() {
    val state = remember { AppState() }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF7F7F9)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Header()

            Group("界面与外观") {
                SwitchRow(
                    title = "顶栏频道隐藏",
                    desc = "隐藏不需要的顶部频道，重启快手生效",
                    checked = state.topBarEnabled,
                    onCheckedChange = state::setTopBarEnabled
                )
                if (state.topBarEnabled) {
                    Chips(
                        items = state.topBarCandidates,
                        selected = state.topBarTabs,
                        onToggle = state::toggleTopBarTab
                    )
                }
                Divider()
                SwitchRow(
                    title = "隐藏中间拍摄按钮",
                    desc = "四个 Tab 平分底栏，不留空档",
                    checked = state.hideShoot,
                    onCheckedChange = state::setHideShoot
                )
                Divider()
                SwitchRow(
                    title = "加号改为双击打开",
                    desc = "300 毫秒内点第二次才打开，防误触",
                    checked = state.shootDoubleTap,
                    onCheckedChange = state::setShootDoubleTap
                )
                Divider()
                SwitchRow(
                    title = "隐藏整个底栏",
                    desc = "视频延伸到底部，只能靠侧滑或返回键切页",
                    checked = state.hideBottomBar,
                    onCheckedChange = state::setHideBottomBar
                )
                Divider()
                SwitchRow(
                    title = "侧边栏选项隐藏",
                    desc = "隐藏不用的分组和选项，重启快手生效",
                    checked = state.sideMenuEnabled,
                    onCheckedChange = state::setSideMenuEnabled
                )
                if (state.sideMenuEnabled) {
                    ChipPairs(
                        items = state.sideMenuGroupCandidates,
                        selected = state.sideMenuGroups,
                        onToggle = state::toggleSideMenuGroup
                    )
                }
                Divider()
                SwitchRow(
                    title = "设置页面换肤",
                    desc = "给快手的设置页换主题色，重进设置页生效",
                    checked = state.skinEnabled,
                    onCheckedChange = state::setSkinEnabled
                )
                if (state.skinEnabled) {
                    ColorRow(
                        colors = listOf(
                            "快手红" to 0xFFFF2D55.toInt(),
                            "松石绿" to 0xFF12B886.toInt(),
                            "葡萄紫" to 0xFF7C4DFF.toInt(),
                            "曜石黑" to 0xFF1F1F1F.toInt(),
                        ),
                        current = state.skinColor,
                        onPick = state::setSkinColor
                    )
                }
            }

            Group("消息与评论") {
                SwitchRow(
                    title = "评论过滤",
                    desc = "按规则隐藏评论，重开评论面板生效",
                    checked = state.commentFilterEnabled,
                    onCheckedChange = state::setCommentFilter
                )
                if (state.commentFilterEnabled) {
                    Label("屏蔽包含关键词（黑名单，每行一个）")
                    MultiLine(
                        value = state.commentBlacklist,
                        placeholder = "广告\n引流\n抽奖",
                        onChange = state::setCommentBlacklist
                    )
                    Label("隐藏过短评论（少于 N 字，0 = 关闭）")
                    SingleLine(
                        value = state.commentMinLen,
                        placeholder = "0",
                        numeric = true,
                        onChange = state::setCommentMinLen
                    )
                    Label("屏蔽指定 IP 属地（每行一个）")
                    MultiLine(
                        value = state.commentRegions,
                        placeholder = "北京\n广东\n海外",
                        onChange = state::setCommentRegions
                    )
                    Divider()
                    SwitchRow(
                        title = "屏蔽广告 / 引流评论",
                        desc = "按内置特征（微信 / QQ / 加群 / 网址等）隐藏",
                        checked = state.commentBlockAds,
                        onCheckedChange = state::setCommentBlockAds
                    )
                }
                Divider()
                SwitchRow(
                    title = "私信防撤回",
                    desc = "对方撤回的私信仍然可见，重启快手生效",
                    checked = state.antiRecall,
                    onCheckedChange = state::setAntiRecall
                )
            }

            Group("视频与浏览") {
                SwitchRow(
                    title = "视频广告拦截",
                    desc = "进播放器前过滤 Feed 里的广告条目",
                    checked = state.feedAdFilter,
                    onCheckedChange = state::setFeedAdFilter
                )
                Divider()
                SwitchRow(
                    title = "视频信息浮层",
                    desc = "播放页显示发布时间、播放进度等信息",
                    checked = state.captionOverlay,
                    onCheckedChange = state::setCaptionOverlay
                )
            }

            Group("模块与维护") {
                InfoRow("适用范围", "快手 / 快手极速版")
                Divider()
                InfoRow("生效方式", "标注重启的项目需完整退出快手后重开")
                Divider()
                InfoRow("日志", "LSPosed → 日志，筛选 NekoraLite")
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "设置写入后由宿主进程下次读取时生效",
                fontSize = 11.sp,
                color = Color(0xFF9E9E9E),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color(0xFFFF2D55), CircleShape)
        ) {
            Text(
                "N",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Nekora Lite", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text("快手增强 · 隐私与界面自定义", fontSize = 12.sp, color = Color(0xFF8A8A8E))
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            fontSize = 12.sp,
            color = Color(0xFF8A8A8E),
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(desc, fontSize = 11.sp, color = Color(0xFF9E9E9E))
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp)
        Text(value, fontSize = 12.sp, color = Color(0xFF8A8A8E))
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFF0F0F2))
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = Color(0xFF8A8A8E),
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun MultiLine(value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, fontSize = 13.sp, color = Color(0xFFC4C4C6)) },
        textStyle = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
    )
}

@Composable
private fun SingleLine(
    value: String,
    placeholder: String,
    numeric: Boolean,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, fontSize = 13.sp, color = Color(0xFFC4C4C6)) },
        textStyle = MaterialTheme.typography.bodySmall,
        singleLine = true,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Chips(items: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRowSimple {
        // 子项由 FlowRow 直接排布
        items.forEach { item ->
            val on = selected.contains(item)
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .background(
                        if (on) Color(0xFFFF2D55) else Color(0xFFF2F2F5),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onToggle(item) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    item,
                    fontSize = 12.sp,
                    color = if (on) Color.White else Color(0xFF4A4A4E)
                )
            }
        }
    }
}

@Composable
private fun ChipPairs(
    items: List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    FlowRowSimple {
        items.forEach { (key, label) ->
            val on = selected.contains(key)
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .background(
                        if (on) Color(0xFFFF2D55) else Color(0xFFF2F2F5),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onToggle(key) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    color = if (on) Color.White else Color(0xFF4A4A4E)
                )
            }
        }
    }
}

/** 流式布局：选项多时自动换行 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowSimple(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        content()
    }
}

@Composable
private fun ColorRow(
    colors: List<Pair<String, Int>>,
    current: Int,
    onPick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        colors.forEach { (name, value) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onPick(value) }
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color(value), CircleShape)
                        .padding(2.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    name,
                    fontSize = 10.sp,
                    color = if (current == value) Color(0xFFFF2D55) else Color(0xFF8A8A8E)
                )
            }
        }
    }
}
