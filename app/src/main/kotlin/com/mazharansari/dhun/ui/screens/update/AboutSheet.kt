// about app, upstream credits and license sheets pushed from the version sheet or the dhun page

package com.mazharansari.dhun.ui.screens.update

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mazharansari.dhun.BuildConfig
import com.mazharansari.dhun.R
import com.mazharansari.dhun.core.updater.GithubOwner
import com.mazharansari.dhun.core.updater.GithubRepo
import com.mazharansari.dhun.core.updater.GithubRepoUrl
import com.mazharansari.dhun.ui.component.PopupSheetState
import com.mazharansari.dhun.ui.component.pressBounce
import com.mazharansari.dhun.ui.player.menu.MenuRowSurface
import com.mazharansari.dhun.ui.screens.donate.showSupportSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

private val DeveloperName = "Mazhar Ansari"

fun PopupSheetState.showAboutSheet(onOpenDevSpace: (Int) -> Unit = {}) {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = "Done", onClick = popup::dismiss) }) {
        AboutSheetContent(popup = popup, onOpenDevSpace = onOpenDevSpace)
    }
}

private fun PopupSheetState.showLicenseSheet() {
    val popup = this
    show(buttonBar = { SheetPrimaryButton(text = "Done", onClick = popup::dismiss) }) {
        LicenseSheetContent()
    }
}

@Composable
private fun ColumnScope.AboutSheetContent(
    popup: PopupSheetState,
    onOpenDevSpace: (Int) -> Unit,
) {
    val installed = shortVersion(BuildConfig.VERSION_NAME)
    val scrollState = rememberScrollState()
    val ringScrollRangePx = with(LocalDensity.current) { 180.dp.toPx() }

    Box(Modifier.fillMaxWidth()) {
        // zero-size layout places the spinning ring behind the top icon without pushing the column down
        VersionRing(
            text = "DHUN $installed",
            scrollProgress = { scrollState.value / ringScrollRangePx },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = 0,
                            minHeight = 0,
                            maxWidth = Constraints.Infinity,
                            maxHeight = Constraints.Infinity,
                        ),
                    )
                    layout(0, 0) {
                        placeable.place(x = -placeable.width / 2, y = (-166).dp.roundToPx())
                    }
                },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            Spacer(Modifier.height(18.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MenuRowSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_dhun_mark),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "About app",
                    fontSize = 36.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.4).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Dhun | $installed",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }

            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MenuRowSurface)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "Dhun is a music player.\nDeveloped by $DeveloperName.",
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp,
                )
            }

            Spacer(Modifier.height(18.dp))
            SectionHeader("made by")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MenuRowSurface)
                    .padding(vertical = 6.dp),
            ) {
                PersonRow(
                    name = DeveloperName,
                    subtitle = "Developer",
                    showArrow = true,
                    onClick = { onOpenDevSpace(0) },
                )
            }

            Spacer(Modifier.height(24.dp))
            // deliberately small and last: the licence notice
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { popup.showLicenseSheet() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Licences",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.2).sp,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ColumnScope.LicenseSheetContent() {
    val context = LocalContext.current
    val licenceText = remember {
        runCatching {
            context.resources.openRawResource(R.raw.license_gpl3).bufferedReader().use { it.readText() }
        }.getOrDefault("GNU General Public License v3.0")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Licences",
            fontSize = 36.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.4).sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Based on open-source projects, GPL-3.0",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = licenceText,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            color = Color.White.copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
        color = Color.White.copy(alpha = 0.55f),
    )
}

@Composable
private fun ArrowBadge() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f)),
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_forward),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun GithubAvatar(
    url: String,
    size: Dp,
    modifier: Modifier = Modifier,
    @DrawableRes fallbackRes: Int? = null,
) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build()
    }
    val fallback = fallbackRes?.let { painterResource(it) }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFD9D9D9)),
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            placeholder = fallback,
            error = fallback,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PersonRow(
    name: String,
    subtitle: String?,
    showArrow: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.98f)
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.dev_mazhar),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFD9D9D9)),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.2).sp,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showArrow) {
            Spacer(Modifier.width(10.dp))
            ArrowBadge()
        }
    }
}
