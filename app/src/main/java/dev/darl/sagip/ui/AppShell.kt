package dev.darl.sagip.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.darl.sagip.chat.ChatScreen
import dev.darl.sagip.chat.ChatViewModel
import dev.darl.sagip.chat.ModelStatus
import dev.darl.sagip.data.Categories
import dev.darl.sagip.data.ContactDirectory
import dev.darl.sagip.data.Illustrations
import dev.darl.sagip.data.KeywordRetriever
import dev.darl.sagip.data.PackRepository
import dev.darl.sagip.data.TopicRepository
import dev.darl.sagip.data.UserProfile
import dev.darl.sagip.ui.components.LocalIllustrations
import dev.darl.sagip.ui.components.LocalLang
import dev.darl.sagip.ui.components.SosPill
import dev.darl.sagip.ui.components.tr
import dev.darl.sagip.ui.screens.CategoryScreen
import dev.darl.sagip.ui.screens.ContactsScreen
import dev.darl.sagip.ui.screens.HomeScreen
import dev.darl.sagip.ui.screens.SearchScreen
import dev.darl.sagip.ui.screens.SosSheet
import dev.darl.sagip.ui.screens.TopicScreen
import dev.darl.sagip.ui.theme.SagipColors
import dev.darl.sagip.ui.theme.SagipTheme
import dev.darl.sagip.ui.theme.Space

enum class Tab { HOME, SEARCH, ASK, CONTACTS }

/** A pushed screen above the current tab. */
sealed interface Detail {
    data class CategoryDetail(val id: String) : Detail
    data class TopicDetail(val id: String) : Detail
}

/** Platform voice hooks, supplied by the Activity. */
class VoiceHooks(
    val start: (languageTag: String, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onState: (Boolean) -> Unit, onError: (String) -> Unit) -> Unit,
    val stop: () -> Unit,
    val openDownloadSettings: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MainShell(profile: UserProfile, voice: VoiceHooks) {
    val context = LocalContext.current.applicationContext
    val lang = profile.preferredLanguage

    val packs = remember { PackRepository.fromAssets(context) }
    val topics = remember(packs) { TopicRepository(packs) }
    val illus = remember { Illustrations(context) }
    val directory = remember { ContactDirectory.fromAssets(context) }
    val vm = remember(profile) {
        ChatViewModel(context, KeywordRetriever(packs), topics, directory, profile).also { it.initEngine() }
    }
    val chat by vm.state.collectAsState()

    var tab by remember { mutableStateOf(Tab.HOME) }
    val stack = remember { mutableStateListOf<Detail>() }
    var showSos by remember { mutableStateOf(false) }

    // Chat input + voice state lives here so it survives tab switches.
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var voiceHint by remember { mutableStateOf<String?>(null) }
    var voiceNeedsPack by remember { mutableStateOf(false) }
    val langTag = if (lang == dev.darl.sagip.data.Lang.TL) "fil-PH" else "en-PH"

    val quickAsks = remember(lang) {
        if (lang == dev.darl.sagip.data.Lang.TL)
            listOf("Matinding pagdurugo", "Paano mag-CPR", "Nabulunan", "Pumapasok ang baha", "Lumilindol", "Nakagat ng ahas", "Nawawala ako sa gubat")
        else
            listOf("Severe bleeding", "How to do CPR", "Someone is choking", "Flood is coming in", "Earthquake now", "Snake bite", "I'm lost in the woods")
    }

    fun ask(q: String) { stack.clear(); tab = Tab.ASK; vm.send(q) }
    fun open(detail: Detail) { stack.add(detail) }

    BackHandler(enabled = stack.isNotEmpty() || tab != Tab.HOME) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else tab = Tab.HOME
    }

    val imeVisible = WindowInsets.isImeVisible

    SagipTheme {
        CompositionLocalProvider(LocalIllustrations provides illus, LocalLang provides lang) {
            Column(Modifier.fillMaxSize().background(SagipColors.Paper)) {
                TopBar(chat.modelName, chat.modelStatus, chat.backend, onSos = { showSos = true })

                Box(Modifier.weight(1f).fillMaxWidth().then(if (tab == Tab.ASK) Modifier.imePadding() else Modifier)) {
                    val top = stack.lastOrNull()
                    when {
                        top is Detail.CategoryDetail -> CategoryScreen(
                            Categories.of(top.id), topics, illus,
                            onBack = { stack.removeAt(stack.lastIndex) },
                            onOpenTopic = { open(Detail.TopicDetail(it)) },
                        )
                        top is Detail.TopicDetail -> topics.get(top.id)?.let { t ->
                            TopicScreen(
                                t, illus,
                                onBack = { stack.removeAt(stack.lastIndex) },
                                onAsk = { ask(it) },
                                onMoreContacts = { stack.clear(); tab = Tab.CONTACTS },
                            )
                        }
                        else -> when (tab) {
                            Tab.HOME -> HomeScreen(
                                profile.name, topics, illus, quickAsks,
                                onOpenCategory = { open(Detail.CategoryDetail(it)) },
                                onSearch = { tab = Tab.SEARCH },
                                onAsk = { ask(it) },
                            )
                            Tab.SEARCH -> SearchScreen(
                                topics, illus,
                                onOpenTopic = { open(Detail.TopicDetail(it)) },
                                onOpenCategory = { open(Detail.CategoryDetail(it)) },
                            )
                            Tab.ASK -> ChatScreen(
                                state = chat, input = input,
                                onInputChange = { input = it; voiceHint = null; voiceNeedsPack = false },
                                onSend = { if (input.isNotBlank()) { vm.send(input); input = ""; voiceHint = null } },
                                listening = listening,
                                quickAsks = quickAsks,
                                onQuickAsk = { vm.send(it) },
                                onOpenTopic = { open(Detail.TopicDetail(it)) },
                                voiceHint = voiceHint,
                                onVoiceHintClick = if (voiceNeedsPack) ({ voice.openDownloadSettings() }) else null,
                                onMic = {
                                    if (listening) { voice.stop(); listening = false } else {
                                        voiceHint = null; voiceNeedsPack = false
                                        voice.start(
                                            langTag, { input = it }, { input = it; listening = false }, { listening = it },
                                            {
                                                listening = false
                                                if (it == "NEEDS_PACK") {
                                                    voiceNeedsPack = true
                                                    voiceHint = tr(lang, "Voice needs a language pack — tap to download", "Kailangan ng language pack — i-tap para i-download")
                                                } else voiceHint = it
                                            },
                                        )
                                    }
                                },
                            )
                            Tab.CONTACTS -> ContactsScreen(directory, profile)
                        }
                    }
                }

                if (!imeVisible) {
                    BottomBar(tab, onSelect = { stack.clear(); tab = it })
                }
            }

            if (showSos) {
                SosSheet(directory, profile, onDismiss = { showSos = false }, onAllContacts = { showSos = false; stack.clear(); tab = Tab.CONTACTS })
            }
        }
    }

    // DEBUG-ONLY headless hook (active only on debuggable builds):
    //   adb shell am broadcast -a dev.darl.sagip.DEBUG_ASK --es q "<query>" -p dev.darl.sagip
    DebugHook(vm) { to ->
        stack.clear(); showSos = false
        when {
            to == "home" -> tab = Tab.HOME
            to == "search" -> tab = Tab.SEARCH
            to == "ask" -> tab = Tab.ASK
            to == "contacts" -> tab = Tab.CONTACTS
            to == "sos" -> showSos = true
            to.startsWith("category:") -> { tab = Tab.HOME; stack.add(Detail.CategoryDetail(to.removePrefix("category:"))) }
            to.startsWith("topic:") -> { tab = Tab.HOME; stack.add(Detail.TopicDetail(to.removePrefix("topic:"))) }
        }
    }
}

@Composable
private fun TopBar(model: String, status: ModelStatus, backend: String, onSos: () -> Unit) {
    val lang = LocalLang.current
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.gutter.dp, vertical = Space.md.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(SagipColors.Coral))
                Spacer(Modifier.width(8.dp))
                Text("sagip", style = MaterialTheme.typography.headlineSmall)
            }
            val s = when (status) {
                ModelStatus.READY -> tr(lang, "Offline · $model${if (backend.isNotEmpty()) " · $backend" else ""}", "Offline · $model${if (backend.isNotEmpty()) " · $backend" else ""}")
                ModelStatus.LOADING -> tr(lang, "Offline · loading $model…", "Offline · nilo-load ang $model…")
                else -> tr(lang, "Offline · guides only", "Offline · mga gabay lang")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (status == ModelStatus.READY) SagipColors.Ok else SagipColors.Muted))
                Spacer(Modifier.width(6.dp))
                Text(s, style = MaterialTheme.typography.labelSmall, color = SagipColors.Muted, maxLines = 1)
            }
        }
        SosPill(onClick = onSos)
    }
}

private data class NavItem(val tab: Tab, val icon: ImageVector, val en: String, val tl: String)

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val lang = LocalLang.current
    val items = listOf(
        NavItem(Tab.HOME, Icons.Outlined.Home, "Home", "Home"),
        NavItem(Tab.SEARCH, Icons.Outlined.Search, "Search", "Hanap"),
        NavItem(Tab.ASK, Icons.AutoMirrored.Outlined.Chat, "Ask", "Tanong"),
        NavItem(Tab.CONTACTS, Icons.Outlined.Contacts, "Contacts", "Contact"),
    )
    Column(Modifier.fillMaxWidth().background(SagipColors.Paper.copy(alpha = 0.96f))) {
        HorizontalDivider(color = SagipColors.Line, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.md.dp, vertical = Space.sm.dp)) {
            items.forEach { it ->
                val on = it.tab == selected
                val label = tr(lang, it.en, it.tl)
                Column(
                    Modifier.weight(1f).heightIn64().clip(RoundedCornerShape(18.dp))
                        .clickable { onSelect(it.tab) }
                        .semantics { contentDescription = label },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.clip(CircleShape).background(if (on) SagipColors.Ink else Color.Transparent)
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                    ) { Icon(it.icon, null, tint = if (on) SagipColors.Acid else SagipColors.Muted, modifier = Modifier.size(24.dp)) }
                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (on) SagipColors.Ink else SagipColors.Muted)
                }
            }
        }
    }
}

private fun Modifier.heightIn64() = this.then(Modifier.height(64.dp))

@Composable
private fun DebugHook(vm: ChatViewModel, onNav: (String) -> Unit) {
    val context = LocalContext.current.applicationContext
    val debuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!debuggable) return
    androidx.compose.runtime.DisposableEffect(vm) {
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                i?.getStringExtra("to")?.let { onNav(it); return }
                val q = i?.getStringExtra("q") ?: return
                android.util.Log.i("SagipTest", "ASK: $q")
                vm.send(q)
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, r, android.content.IntentFilter("dev.darl.sagip.DEBUG_ASK"),
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(r) } }
    }
    val state by vm.state.collectAsState()
    LaunchedEffect(state.busy, state.messages.size) {
        val last = state.messages.lastOrNull()
        if (!state.busy && last != null && last.role == dev.darl.sagip.chat.Role.ASSISTANT && !last.streaming) {
            android.util.Log.i("SagipTest", "DONE model=${state.modelName}/${state.backend} sev=${last.severity} related=${last.related.map { it.title }} contacts=${last.contacts.map { it.label }}\n>>>\n${last.text}\n<<<")
        }
    }
}
