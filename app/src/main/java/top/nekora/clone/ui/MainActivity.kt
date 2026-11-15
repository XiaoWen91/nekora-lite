package top.nekora.clone.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.nekora.clone.state.ConfigStore

/**
 * 桌面 App 界面。
 *
 * 与原版一致：**纯展示页，没有任何功能开关**。
 * 只展示：
 *   - 应用名与版本
 *   - 支持的宿主列表
 *   - 发行仓库 / 交流群 / 频道
 *   - 使用提示
 *
 * 真正的设置面板在快手内部：设置页一行入口 / 评论区右上角 / 分享面板。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ConfigStore.initInApp(this)
        setContent {
            HomeScreen(this)
        }
    }
}

private val BG = Color(0xFFF7F7F9)
private val CARD = Color.White
private val TEXT_MAIN = Color(0xFF1A1A1A)
private val TEXT_SUB = Color(0xFF8A8A8E)
private val ACCENT = Color(0xFF2B6BE4)

private const val REPO_URL = "https://github.com/XiaoWen91/nekora-lite"
private const val TG_URL = "https://t.me/mayi_miku"

@Composable
private fun HomeScreen(ctx: Context) {
    Surface(modifier = Modifier.fillMaxSize(), color = BG) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(40.dp))

            // ---------- 头部 ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(ACCENT, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("N", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "Nekora Lite",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TEXT_MAIN
                    )
                    Spacer(Modifier.height(3.dp))
                    Text("快手增强模块 · libxposed API 102", fontSize = 12.sp, color = TEXT_SUB)
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---------- 支持的宿主 ----------
            SectionTitle("支持的宿主")
            Card {
                HostRow("快手极速版", "com.kuaishou.nebula")
                Divider()
                HostRow("快手标准版", "com.smile.gifmaker")
            }

            Spacer(Modifier.height(18.dp))

            // ---------- 入口说明 ----------
            SectionTitle("入口位置")
            Card {
                InfoRow("设置页", "快手 → 设置 → 账号 → Xposed 自定义设置")
                Divider()
                InfoRow("评论区", "评论面板右上角轻点")
                Divider()
                InfoRow("分享面板", "分享 → 模块设置")
            }

            Spacer(Modifier.height(18.dp))

            // ---------- 链接 ----------
            SectionTitle("获取更新")
            Card {
                LinkRow("发行仓库", REPO_URL) { openUrl(ctx, REPO_URL) }
                Divider()
                LinkRow("Telegram 频道", "@mayi_miku") { openUrl(ctx, TG_URL) }
            }

            Spacer(Modifier.height(18.dp))

            // ---------- 提示 ----------
            Card {
                Box(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "本模块需要在 LSPosed 中启用，并将作用域勾选为快手 / 快手极速版。\n" +
                            "修改顶栏、底栏、侧边栏、私信防撤回后，需要完整退出快手进程再重新打开才生效。\n\n" +
                            "部分功能（免看广告、评论推送、批量私信、用户数据上传）不包含在本项目中。",
                        fontSize = 12.sp,
                        color = TEXT_SUB,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = TEXT_SUB,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CARD, RoundedCornerShape(14.dp))
    ) {
        Column { content() }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(Color(0xFFF0F0F2))
    )
}

@Composable
private fun HostRow(name: String, pkg: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, fontSize = 15.sp, color = TEXT_MAIN)
        Text(pkg, fontSize = 11.sp, color = TEXT_SUB)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(label, fontSize = 15.sp, color = TEXT_MAIN)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 12.sp, color = TEXT_SUB)
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = TEXT_MAIN)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, fontSize = 11.sp, color = TEXT_SUB)
        }
        Text("›", fontSize = 20.sp, color = Color(0xFFC0C0C4))
    }
}

private fun openUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }.onFailure {
        Toast.makeText(ctx, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}
