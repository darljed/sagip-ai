package dev.darl.sagip.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.darl.sagip.data.Categories
import dev.darl.sagip.data.Category
import dev.darl.sagip.data.CategoryGroup
import dev.darl.sagip.data.Illustrations
import dev.darl.sagip.data.Lang
import dev.darl.sagip.data.Severity
import dev.darl.sagip.data.Topic
import dev.darl.sagip.data.TopicRepository
import dev.darl.sagip.ui.components.CallRow
import dev.darl.sagip.ui.components.CategoryCard
import dev.darl.sagip.ui.components.ImageSlot
import dev.darl.sagip.ui.components.LocalLang
import dev.darl.sagip.ui.components.PillChip
import dev.darl.sagip.ui.components.SeverityTag
import dev.darl.sagip.ui.components.TopicCard
import dev.darl.sagip.ui.components.staggerHeight
import dev.darl.sagip.ui.components.tr
import dev.darl.sagip.ui.theme.SagipColors
import dev.darl.sagip.ui.theme.Space

private val G = Space.gutter.dp

@Composable
private fun BackRow(onBack: () -> Unit, title: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, tr(LocalLang.current, "Back", "Bumalik"), tint = SagipColors.Ink) }
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ───────────────────────── Home ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    name: String,
    repo: TopicRepository,
    illus: Illustrations,
    quickAsks: List<String>,
    onOpenCategory: (String) -> Unit,
    onSearch: () -> Unit,
    onAsk: (String) -> Unit,
) {
    val lang = LocalLang.current
    val cats = remember(repo) { Categories.present(repo.topics) }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = G, end = G, top = Space.md.dp, bottom = Space.xl.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalItemSpacing = 14.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.lg.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs.dp)) {
                    Text(
                        if (name.isNotBlank()) tr(lang, "Stay ready, ${name.substringBefore(' ')}.", "Maging handa, ${name.substringBefore(' ')}.")
                        else tr(lang, "Stay ready.", "Maging handa."),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        tr(lang, "${repo.topics.size} guides · works offline", "${repo.topics.size} gabay · gumagana kahit walang internet"),
                        style = MaterialTheme.typography.bodyMedium, color = SagipColors.Muted,
                    )
                }
                SearchField(onClick = onSearch)
                Text(tr(lang, "Ask quickly", "Mabilis na tanong"), style = MaterialTheme.typography.titleSmall, color = SagipColors.Muted)
            }
        }
        item(span = StaggeredGridItemSpan.FullLine) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(quickAsks) { q -> PillChip(q, selected = false, onClick = { onAsk(q) }) }
            }
        }
        CategoryGroup.entries.forEach { group ->
            val inGroup = cats.filter { it.group == group }
            if (inGroup.isNotEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Text(
                        if (lang == Lang.TL) group.tl else group.en,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = Space.lg.dp),
                    )
                }
                items(inGroup, key = { it.id }) { c ->
                    CategoryCard(
                        c, repo.inCategory(c.id).size, illus.categoryPath(c.id),
                        imageHeight = staggerHeight(c.id, base = 110, step = 30),
                        onClick = { onOpenCategory(c.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(onClick: () -> Unit) {
    val lang = LocalLang.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CircleShape).background(SagipColors.Card)
            .border(1.dp, SagipColors.Line, CircleShape).clickable(onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = SagipColors.Muted)
        Spacer(Modifier.width(12.dp))
        Text(tr(lang, "Search guides — bleeding, flood, snake…", "Maghanap — dugo, baha, ahas…"), style = MaterialTheme.typography.bodyMedium, color = SagipColors.Muted, maxLines = 1)
    }
}

// ───────────────────────── Category ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryScreen(
    category: Category,
    repo: TopicRepository,
    illus: Illustrations,
    onBack: () -> Unit,
    onOpenTopic: (String) -> Unit,
) {
    val lang = LocalLang.current
    val topics = remember(category) { repo.inCategory(category.id).sortedByDescending { it.severity.ordinal } }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = G, end = G, bottom = Space.xl.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalItemSpacing = 14.dp,
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.lg.dp)) {
                BackRow(onBack)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.lg.dp), verticalAlignment = Alignment.CenterVertically) {
                    ImageSlot(
                        illus.categoryPath(category.id), category.tint, category.icon,
                        Modifier.size(112.dp).clip(RoundedCornerShape(24.dp)),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs.dp)) {
                        Text(category.title(lang), style = MaterialTheme.typography.headlineMedium)
                        if (category.blurb(lang).isNotBlank()) Text(category.blurb(lang), style = MaterialTheme.typography.bodyMedium, color = SagipColors.Muted)
                        Text(tr(lang, "${topics.size} guides", "${topics.size} gabay"), style = MaterialTheme.typography.labelMedium, color = SagipColors.Muted)
                    }
                }
                Spacer(Modifier.height(Space.xs.dp))
            }
        }
        items(topics, key = { it.id }) { t ->
            TopicCard(t, category, illus.heroPath(t.key), staggerHeight(t.id, base = 100, step = 26), onClick = { onOpenTopic(t.id) })
        }
    }
}

// ───────────────────────── Topic detail ─────────────────────────

@Composable
fun TopicScreen(
    topic: Topic,
    illus: Illustrations,
    onBack: () -> Unit,
    onAsk: (String) -> Unit,
    onMoreContacts: () -> Unit,
) {
    val lang = LocalLang.current
    val cat = Categories.of(topic.category)
    val steps = topic.steps(lang)
    val extras = remember(topic) { illus.extraPaths(topic.key) }
    // "<topic>.step3.png" belongs under step 3; every other extra is a reference image.
    val stepImg = remember(topic) { illus.stepPaths(topic.key) }
    val refs = extras

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Space.xxl.dp),
        verticalArrangement = Arrangement.spacedBy(Space.lg.dp),
    ) {
        item { BackRow(onBack, topic.title(lang)) }
        val hero = illus.heroPath(topic.key)
        if (hero != null) {
            item {
                ImageSlot(
                    hero, cat.tint, cat.icon,
                    Modifier.padding(horizontal = G).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(26.dp)),
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = G), verticalArrangement = Arrangement.spacedBy(Space.md.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SeverityTag(topic.severity)
                    Text(cat.title(lang), style = MaterialTheme.typography.labelMedium, color = SagipColors.Muted)
                }
                Text(topic.title(lang), style = MaterialTheme.typography.headlineLarge)
            }
        }
        if (topic.callEmergency) {
            item {
                Column(Modifier.padding(horizontal = G), verticalArrangement = Arrangement.spacedBy(Space.sm.dp)) {
                    Text(tr(lang, "Need help right now?", "Kailangan ng tulong ngayon?"), style = MaterialTheme.typography.titleSmall)
                    CallRow(tr(lang, "Call 911", "Tumawag sa 911"), "911", tr(lang, "Emergency hotline", "Pang-emergency na hotline"), emphasis = true)
                    Text(
                        tr(lang, "More contacts", "Iba pang contact"),
                        style = MaterialTheme.typography.labelLarge, color = SagipColors.Blue,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onMoreContacts).heightIn(min = 44.dp).padding(vertical = 10.dp),
                    )
                }
            }
        }
        item {
            Text(
                tr(lang, "What to do", "Ano ang gagawin"), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = G).padding(top = Space.sm.dp),
            )
        }
        items(steps.size) { i ->
            StepCard(i + 1, steps[i], stepImg[i + 1], cat)
        }
        if (refs.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Space.md.dp)) {
                    Text(
                        tr(lang, "Reference images", "Mga larawang gabay"), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = G),
                    )
                    LazyRow(contentPadding = PaddingValues(horizontal = G), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(refs) { p ->
                            ImageSlot(p, cat.tint, cat.icon, Modifier.width(260.dp).height(190.dp).clip(RoundedCornerShape(22.dp)))
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = G), verticalArrangement = Arrangement.spacedBy(Space.lg.dp)) {
                Text(
                    tr(lang, "Source: ", "Pinagkunan: ") + topic.source(lang),
                    style = MaterialTheme.typography.bodySmall, color = SagipColors.Muted,
                )
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(CircleShape).background(SagipColors.Ink)
                        .clickable {
                            onAsk(tr(lang, "Tell me more about: ${topic.title(Lang.EN)}", "Ipaliwanag mo pa: ${topic.title(Lang.TL)}"))
                        }.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = SagipColors.InkAccent)
                    Spacer(Modifier.width(10.dp))
                    Text(tr(lang, "Ask SAGIP about this", "Magtanong sa SAGIP tungkol dito"), style = MaterialTheme.typography.labelLarge, color = SagipColors.OnInk)
                }
            }
        }
    }
}

@Composable
private fun StepCard(n: Int, text: String, imagePath: String?, cat: Category) {
    val lang = LocalLang.current
    Column(
        Modifier.padding(horizontal = G).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(SagipColors.Card)
            .border(1.dp, SagipColors.Line, RoundedCornerShape(22.dp)),
    ) {
        if (imagePath != null) {
            ImageSlot(imagePath, cat.tint, cat.icon, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
        }
        Row(Modifier.padding(Space.lg.dp), horizontalArrangement = Arrangement.spacedBy(Space.md.dp)) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(SagipColors.Acid)
                    .semantics { contentDescription = tr(lang, "Step $n", "Hakbang $n") },
                contentAlignment = Alignment.Center,
            ) { Text("$n", style = MaterialTheme.typography.labelLarge, color = SagipColors.OnAcid) }
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

// ───────────────────────── Search ─────────────────────────

@Composable
fun SearchScreen(repo: TopicRepository, illus: Illustrations, onOpenTopic: (String) -> Unit, onOpenCategory: (String) -> Unit) {
    val lang = LocalLang.current
    var q by remember { mutableStateOf("") }
    val results = remember(q) { if (q.length >= 2) repo.search(q) else emptyList() }
    Column(Modifier.fillMaxSize().padding(horizontal = G), verticalArrangement = Arrangement.spacedBy(Space.lg.dp)) {
        Text(tr(lang, "Search", "Maghanap"), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = Space.md.dp))
        TextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { Text(tr(lang, "Bleeding, flood, snake…", "Dugo, baha, ahas…"), color = SagipColors.Muted) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = SagipColors.Muted) },
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(),
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SagipColors.Card, unfocusedContainerColor = SagipColors.Card,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).border(1.dp, SagipColors.Line, CircleShape),
        )
        if (q.length < 2) {
            Text(tr(lang, "Browse by category", "Mag-browse ayon sa kategorya"), style = MaterialTheme.typography.titleSmall, color = SagipColors.Muted)
            val cats = remember(repo) { Categories.present(repo.topics) }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cats.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { c -> PillChip(c.title(lang), false, { onOpenCategory(c.id) }) }
                    }
                }
            }
        } else if (results.isEmpty()) {
            Text(tr(lang, "No guide found. Try Ask — SAGIP can help with other concerns.", "Walang nahanap. Subukan ang Magtanong."), style = MaterialTheme.typography.bodyMedium, color = SagipColors.Muted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = Space.xl.dp)) {
                items(results, key = { it.id }) { t ->
                    val cat = Categories.of(t.category)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SagipColors.Card)
                            .border(1.dp, SagipColors.Line, RoundedCornerShape(20.dp)).clickable { onOpenTopic(t.id) }.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ImageSlot(illus.heroPath(t.key), cat.tint, cat.icon, Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(t.title(lang), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                SeverityTag(t.severity)
                            }
                            Text(cat.title(lang), style = MaterialTheme.typography.labelSmall, color = SagipColors.Muted)
                        }
                    }
                }
            }
        }
    }
}
