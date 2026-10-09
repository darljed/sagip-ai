package dev.darl.sagip.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level shelves on the Home screen. */
enum class CategoryGroup(val en: String, val tl: String) {
    EMERGENCY("Emergencies", "Mga Emergency"),
    DISASTER("Disasters", "Mga Sakuna"),
    SURVIVAL("Survival", "Pangkabuhayan sa Gubat/Dagat"),
}

/**
 * A category = a pack (or the pack's schema-v2 `category` override). Unknown ids still
 * render via [Categories.of], so packs added later appear in the app automatically.
 */
data class Category(
    val id: String,
    val en: String,
    val tl: String,
    val blurbEn: String,
    val blurbTl: String,
    val group: CategoryGroup,
    val icon: ImageVector,
    /** Soft tint used behind the illustration placeholder. */
    val tint: Color,
) {
    fun title(lang: Lang) = if (lang == Lang.TL) tl else en
    fun blurb(lang: Lang) = if (lang == Lang.TL) blurbTl else blurbEn
}

object Categories {
    val all: List<Category> = listOf(
        Category("first_aid", "First Aid", "Pangunang Lunas", "Bleeding, CPR, choking, burns and more", "Pagdurugo, CPR, nabulunan, paso at iba pa", CategoryGroup.EMERGENCY, Icons.Outlined.LocalHospital, Color(0xFFFFD9D4)),
        Category("fire", "Fire", "Sunog", "Escape and fire response", "Pagtakas at tamang aksyon sa sunog", CategoryGroup.EMERGENCY, Icons.Outlined.LocalFireDepartment, Color(0xFFFFE0C2)),
        Category("home", "At Home", "Sa Bahay", "Household hazards and safety", "Panganib at kaligtasan sa bahay", CategoryGroup.EMERGENCY, Icons.Outlined.Home, Color(0xFFE6E1FF)),
        Category("vehicle", "On the Road", "Sa Daan", "Accidents and vehicle emergencies", "Aksidente at emergency sa sasakyan", CategoryGroup.EMERGENCY, Icons.Outlined.DirectionsCar, Color(0xFFD9ECFF)),
        Category("public", "In Public", "Sa Publiko", "Crowds, threats and public safety", "Dagsa ng tao at kaligtasan sa publiko", CategoryGroup.EMERGENCY, Icons.Outlined.Groups, Color(0xFFE5F3D9)),
        Category("workplace", "At Work", "Sa Trabaho", "Workplace injuries and hazards", "Aksidente at panganib sa trabaho", CategoryGroup.EMERGENCY, Icons.Outlined.Engineering, Color(0xFFFFF0B8)),
        Category("typhoon_flood", "Typhoon & Flood", "Bagyo at Baha", "Prepare, evacuate, stay safe", "Maghanda, lumikas, manatiling ligtas", CategoryGroup.DISASTER, Icons.Outlined.Thunderstorm, Color(0xFFCFE6FF)),
        Category("earthquake", "Earthquake", "Lindol", "During, after, and aftershocks", "Habang at pagkatapos ng lindol", CategoryGroup.DISASTER, Icons.Outlined.Vibration, Color(0xFFFFE0C2)),
        Category("volcano", "Volcano", "Bulkan", "Ashfall and eruption safety", "Abo at kaligtasan sa pagputok", CategoryGroup.DISASTER, Icons.Outlined.Landscape, Color(0xFFFFD2C4)),
        Category("survival", "Survival Basics", "Mga Batayan ng Survival", "Water, shelter, fire, signals", "Tubig, silungan, apoy, hudyat", CategoryGroup.SURVIVAL, Icons.Outlined.Hiking, Color(0xFFE2EFC9)),
        Category("wilderness", "Wilderness", "Gubat at Bundok", "Lost, weather and terrain", "Naligaw, panahon at lupain", CategoryGroup.SURVIVAL, Icons.Outlined.Forest, Color(0xFFD4EBD0)),
        Category("sea", "Sea & Water", "Dagat at Tubig", "Drowning, boats and open water", "Pagkalunod, bangka at bukas na tubig", CategoryGroup.SURVIVAL, Icons.Outlined.Sailing, Color(0xFFCDEBF2)),
        Category("wildlife", "Wildlife", "Mga Hayop", "Snakes and animal encounters", "Ahas at pakikitagpo sa hayop", CategoryGroup.SURVIVAL, Icons.Outlined.Pets, Color(0xFFEFE2C6)),
    )
    private val byId = all.associateBy { it.id }

    /** Known category, or a generic one for a pack the app has not heard of yet. */
    fun of(id: String): Category = byId[id] ?: Category(
        id, id.replace('_', ' ').replaceFirstChar { it.uppercase() }, id.replace('_', ' ').replaceFirstChar { it.uppercase() },
        "", "", CategoryGroup.EMERGENCY, Icons.Outlined.Category, Color(0xFFE9E7DF),
    )

    /** Categories that actually have topics, in display order (known first, then new). */
    fun present(topics: List<Topic>): List<Category> {
        val ids = topics.map { it.category }.distinct()
        val known = all.filter { it.id in ids }
        val extra = ids.filter { it !in byId }.map { of(it) }
        return known + extra
    }
}
