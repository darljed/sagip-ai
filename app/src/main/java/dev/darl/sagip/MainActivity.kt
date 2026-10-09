package dev.darl.sagip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.darl.sagip.ui.theme.SagipColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SagipFirstScreen() }
    }
}

/**
 * Phase 0 checkpoint screen: proves the app builds, installs, launches, and that
 * the SAGIP design tokens render. Deliberately minimal — the chat UI lands in
 * later phases. Carries the two signature visual cues already:
 *  - "Offline · Ready" green status dot (our differentiator, adapted from the
 *    reference's "Online" dot)
 *  - the accent "orb" gradient (the AI presence).
 */
@Composable
fun SagipFirstScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SagipColors.CanvasGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Accent orb (AI presence)
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(SagipColors.OrbGradient)
            )
            Spacer(Modifier.height(28.dp))

            Text(
                text = "SAGIP",
                color = SagipColors.Text,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Smart Aid & Guidance for Immediate Preparedness",
                color = SagipColors.TextDim,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(24.dp))

            // "Offline · Ready" status pill — the core promise, visible on first launch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(SagipColors.SurfaceStrong)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(SagipColors.Ok)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Offline · Ready",
                    color = SagipColors.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
