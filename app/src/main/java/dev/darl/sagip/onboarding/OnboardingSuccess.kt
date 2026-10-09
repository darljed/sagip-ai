package dev.darl.sagip.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.darl.sagip.data.Lang
import dev.darl.sagip.data.UserProfile
import dev.darl.sagip.ui.theme.SagipColors

/** Confirms the profile was saved locally, then enters the chat. */
@Composable
fun OnboardingSuccessScreen(profile: UserProfile, onEnter: () -> Unit) {
    val tl = profile.preferredLanguage == Lang.TL
    Box(
        Modifier.fillMaxSize().background(SagipColors.CanvasGradient)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(SagipColors.Ok), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF05240F), modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                if (tl) "Handa na ang SAGIP!" else "You're all set!",
                color = SagipColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (tl)
                    "Naka-save sa device mo ang iyong impormasyon — hindi ito umaalis sa telepono. Gagamitin ko ito para iakma ang payo sa'yo."
                else
                    "Your info is saved on your device — it never leaves the phone. I'll use it to tailor my guidance to you.",
                color = SagipColors.TextDim, fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            if (profile.name.isNotBlank()) {
                Text(
                    (if (tl) "Mag-ingat, " else "Stay safe, ") + profile.name + ".",
                    color = SagipColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(36.dp))
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(SagipColors.Accent)
                    .clickable { onEnter() }.padding(horizontal = 40.dp, vertical = 14.dp)
            ) {
                Text(if (tl) "Simulan" else "Enter SAGIP", color = SagipColors.OnInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

