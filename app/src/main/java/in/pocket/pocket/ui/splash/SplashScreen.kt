package `in`.marxen.pocket.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.marxen.pocket.R
import `in`.marxen.pocket.ui.theme.PocketBeige
import `in`.marxen.pocket.ui.theme.PocketGreen
import `in`.marxen.pocket.ui.theme.PocketGreenLight
import `in`.marxen.pocket.ui.theme.PocketText
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onReady: () -> Unit) {
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(800))
        delay(1000)
        onReady()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketBeige)
            .alpha(alpha.value)
    ) {
        // Top-left leaf accent
        Box(
            modifier = Modifier
                .size(160.dp)
                .offset(x = (-40).dp, y = (-40).dp)
                .rotate(15f)
                .clip(CircleShape)
                .background(PocketGreenLight.copy(alpha = 0.3f))
        )

        // Bottom-right leaf accent
        Box(
            modifier = Modifier
                .size(140.dp)
                .offset(x = 280.dp, y = 660.dp)
                .rotate(-25f)
                .clip(CircleShape)
                .background(PocketGreenLight.copy(alpha = 0.25f))
        )

        // Small accent circle near bottom-left
        Box(
            modifier = Modifier
                .size(80.dp)
                .offset(x = 20.dp, y = 700.dp)
                .clip(CircleShape)
                .background(PocketGreenLight.copy(alpha = 0.15f))
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Pocket",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = PocketGreen,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Image(
                painter = painterResource(id = R.drawable.splash_wallet),
                contentDescription = "Wallet illustration",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(180.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your money, your view.",
                style = MaterialTheme.typography.bodyLarge,
                color = PocketText,
            )

            Spacer(modifier = Modifier.height(64.dp))

            Text(
                text = "Track today.",
                style = MaterialTheme.typography.bodyMedium,
                color = PocketText,
            )
            Text(
                text = "Live freely tomorrow.",
                style = MaterialTheme.typography.bodyMedium,
                color = PocketText,
            )
        }
    }
}
