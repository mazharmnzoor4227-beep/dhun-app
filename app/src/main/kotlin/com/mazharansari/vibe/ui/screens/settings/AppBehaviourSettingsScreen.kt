package com.mazharansari.vibe.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mazharansari.vibe.R
import com.mazharansari.vibe.constants.AlbumsFromLikedSongsKey
import com.mazharansari.vibe.constants.DataSaverKey
import com.mazharansari.vibe.constants.DataSaverMobileOnlyKey
import com.mazharansari.vibe.constants.HideSettingsDescriptionsKey
import com.mazharansari.vibe.constants.HideUpdateNoticeKey
import com.mazharansari.vibe.constants.LarpLevelKey
import com.mazharansari.vibe.constants.LarpModeKey
import com.mazharansari.vibe.constants.LocalSongAutoMetadataKey
import com.mazharansari.vibe.constants.LyricsOrderOverrideKey
import com.mazharansari.vibe.constants.LyricsProviderOrderKey
import com.mazharansari.vibe.constants.PreferredLyricsProvider
import com.mazharansari.vibe.constants.PreferredLyricsProviderKey
import com.mazharansari.vibe.constants.OfflineModeKey
import com.mazharansari.vibe.constants.PauseSearchHistoryKey
import com.mazharansari.vibe.constants.ProxyEnabledKey
import com.mazharansari.vibe.constants.ProxyUrlKey
import com.mazharansari.vibe.constants.SYSTEM_DEFAULT
import com.mazharansari.vibe.constants.SkipSilenceKey
import com.mazharansari.vibe.constants.SponsorBlockCategories
import com.mazharansari.vibe.constants.StreamFailureTimeoutKey
import com.mazharansari.vibe.constants.SponsorBlockEnabledKey
import com.mazharansari.vibe.constants.YtReportPlaysKey
import com.mazharansari.vibe.core.updater.saveUpdateNotificationsSetting
import com.mazharansari.vibe.i18n.AppLanguage
import com.mazharansari.vibe.i18n.activityOrNull
import com.mazharansari.vibe.lyrics.LyricsProviderRegistry
import com.mazharansari.vibe.lyrics.YouTubeCaptionsLyricsProvider
import com.mazharansari.vibe.ui.component.AppSwitch
import com.mazharansari.vibe.ui.component.LocalZoomOutOverlayState
import com.mazharansari.vibe.ui.component.SettingsItem
import com.mazharansari.vibe.ui.component.SubSettingsScaffold
import com.mazharansari.vibe.ui.component.SubSettingsSearchBar
import com.mazharansari.vibe.ui.player.LarpLevel
import com.mazharansari.vibe.utils.AppProxy
import com.mazharansari.vibe.utils.rememberEnumPreference
import com.mazharansari.vibe.utils.rememberPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// settings > app behaviour: the app itself, its network and your library; playback and the player's looks have their own pages now
@Composable
fun AppBehaviourSettingsScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }

    SubSettingsScaffold(
        title = stringResource(R.string.settings_app_behaviour),
        onBack = { navController.navigateUp() },
        searchBar = { SubSettingsSearchBar(query = query, onQueryChange = { query = it }) },
    ) {
        // larpaspriority
        SettingsSection(title = null, items = larpItems().matching(query))
        SettingsSection(stringResource(R.string.general_app_settings), generalItems().matching(query))
        SettingsSection(stringResource(R.string.settings_section_network), networkItems(navController).matching(query))
        SettingsSection(stringResource(R.string.settings_section_library_privacy), libraryItems().matching(query))
        SettingsSection(stringResource(R.string.settings_section_lyrics), lyricsItems().matching(query))
        SettingsSection(stringResource(R.string.settings_related), relatedItems(navController).matching(query))
        Spacer(Modifier.height(140.dp))
    }
}

// there's no limit to the larp
@Composable
private fun larpItems(): List<SettingsItem> {
    val (larping, setLarping) = rememberPreference(LarpModeKey, defaultValue = false)
    val (level, setLevel) = rememberEnumPreference(LarpLevelKey, defaultValue = LarpLevel.MAXXING)
    val overlay = LocalZoomOutOverlayState.current
    // the design's plain circle, nothing drawn in it
    val blank = remember { ColorPainter(Color.Transparent) }
    val openSheet = {
        overlay.showLarpSheet(current = level) { picked ->
            setLevel(picked)
            setLarping(true)
        }
    }
    val title = stringResource(R.string.larp_mode)
    return listOf(
        // the row opens the sheet to change level too, the switch only turns it off without asking
        SettingsItem(
            title = { Text(title) },
            highlightKey = "Larp mode",
            searchText = title,
            descriptionText = if (larping) stringResource(level.title) else stringResource(R.string.larp_mode_desc),
            detail = stringResource(R.string.larp_mode_detail),
            icon = blank,
            iconShape = CircleShape,
            onClick = openSheet,
            trailingContent = {
                AppSwitch(checked = larping, onCheckedChange = { on -> if (on) openSheet() else setLarping(false) })
            },
        ),
    )
}

@Composable
private fun generalItems(): List<SettingsItem> {
    val context = LocalContext.current
    val (noticeHidden, onNoticeHidden) = rememberPreference(HideUpdateNoticeKey, defaultValue = false)
    val (hideDescriptions, onHideDescriptions) = rememberPreference(HideSettingsDescriptionsKey, defaultValue = true)
    // the launch check's notification reads its own switch, so it follows this one too
    val setNoticeHidden = { hidden: Boolean ->
        onNoticeHidden(hidden)
        saveUpdateNotificationsSetting(context, !hidden)
    }
    return listOf(
        languageItem(),
        roomsItem(),
        switchItem(
            key = "Disable App Updates notice",
            title = stringResource(R.string.settings_updates_notice),
            description = stringResource(R.string.settings_updates_notice_desc),
            icon = painterResource(R.drawable.update),
            checked = noticeHidden,
            onCheckedChange = setNoticeHidden,
        ),
        switchItem(
            key = "Disable description on settings",
            title = stringResource(R.string.settings_hide_descriptions),
            description = stringResource(R.string.settings_hide_descriptions_desc),
            icon = painterResource(R.drawable.info),
            checked = hideDescriptions,
            onCheckedChange = onHideDescriptions,
        ),
    )
}

@Composable
private fun roomsItem(): SettingsItem = SettingsItem(
    title = { Text(stringResource(R.string.rooms_menu)) },
    highlightKey = "Rooms",
    descriptionText = stringResource(R.string.rooms_settings_desc),
    searchText = stringResource(R.string.rooms_menu),
    icon = painterResource(R.drawable.group),
    iconShape = CircleShape,
    onClick = { com.mazharansari.vibe.ui.player.menu.RoomsUi.show() },
)

// the app's own language, apart from the phone's; android 13+ keeps it in the system's per-app setting too
@Composable
private fun languageItem(): SettingsItem {
    val overlay = LocalZoomOutOverlayState.current
    val context = LocalContext.current
    val activity = remember(context) { context.activityOrNull() }
    val language = remember(context) { AppLanguage.selection(context) }
    val languageName = if (language == SYSTEM_DEFAULT) stringResource(R.string.system_default) else appLanguageNativeName(language)
    val failure = stringResource(R.string.app_language_update_failed)
    val scope = rememberCoroutineScope()
    val pick = { tag: String ->
        if (tag != language && activity != null) {
            scope.launch {
                try {
                    AppLanguage.select(activity, tag)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Toast.makeText(context, failure, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    return SettingsItem(
        title = { Text(stringResource(R.string.app_language)) },
        highlightKey = "App language",
        descriptionText = languageName,
        searchText = stringResource(R.string.app_language),
        icon = painterResource(R.drawable.translate),
        iconShape = CircleShape,
        enabled = activity != null,
        onClick = { overlay.showAppLanguageSheet(selected = language, onSelect = { pick(it) }) },
    )
}

@Composable
private fun networkItems(navController: NavController): List<SettingsItem> {
    val (offlineMode, onOfflineModeChange) = rememberPreference(OfflineModeKey, defaultValue = false)
    val (dataSaver, onDataSaverChange) = rememberPreference(DataSaverKey, defaultValue = false)
    val (dataSaverMobileOnly, onDataSaverMobileOnlyChange) = rememberPreference(DataSaverMobileOnlyKey, defaultValue = true)
    val (proxyOn) = rememberPreference(ProxyEnabledKey, defaultValue = false)
    val (proxyAddress) = rememberPreference(ProxyUrlKey, defaultValue = "")
    return listOf(
        switchItem(
            key = "Offline mode",
            title = stringResource(R.string.settings_offline_mode),
            description = stringResource(R.string.settings_offline_mode_desc),
            icon = painterResource(R.drawable.download),
            checked = offlineMode,
            onCheckedChange = onOfflineModeChange,
        ),
        // lighter streams and still covers, for metered plans; the second switch decides whether wi-fi counts too
        switchItem(
            key = "Data saver",
            title = stringResource(R.string.settings_data_saver),
            description = stringResource(R.string.settings_data_saver_desc),
            detail = stringResource(R.string.settings_data_saver_detail),
            icon = rememberVectorPainter(Icons.Rounded.DataSaverOn),
            checked = dataSaver,
            onCheckedChange = onDataSaverChange,
        ),
        switchItem(
            key = "Data saver only on mobile data",
            title = stringResource(R.string.settings_data_saver_mobile),
            description = stringResource(R.string.settings_data_saver_mobile_desc),
            icon = rememberVectorPainter(Icons.Rounded.SignalCellularAlt),
            checked = dataSaverMobileOnly,
            onCheckedChange = onDataSaverMobileOnlyChange,
            subOptionOf = dataSaver,
        ),
        linkItem(
            key = "Proxy",
            title = stringResource(R.string.proxy),
            description = if (proxyOn && AppProxy.parseAddress(proxyAddress) != null) {
                stringResource(R.string.proxy_status_on, proxyAddress)
            } else {
                stringResource(R.string.proxy_status_off)
            },
            icon = painterResource(R.drawable.wifi_proxy),
            onClick = { navController.navigate(ProxySettingsRoute) },
        ),
    )
}

@Composable
private fun libraryItems(): List<SettingsItem> {
    val (albumsFromLikes, onAlbumsFromLikesChange) = rememberPreference(AlbumsFromLikedSongsKey, defaultValue = false)
    val (autoMetadata, onAutoMetadataChange) = rememberPreference(LocalSongAutoMetadataKey, defaultValue = true)
    val (pauseSearchHistory, onPauseSearchHistoryChange) = rememberPreference(PauseSearchHistoryKey, defaultValue = false)
||||||| /tmp/base.f
private fun playerBehaviourItems(): List<SettingsItem> {
    val (disableVideoAutoplay, onDisableVideoAutoplayChange) = rememberPreference(DisableVideoAutoplayKey, defaultValue = false)
    val (youtubeTranslations, onYoutubeTranslationsChange) = rememberPreference(YouTubeCaptionTranslationKey, defaultValue = true)
    val (syncLyricsToVideo, onSyncLyricsToVideoChange) = rememberPreference(SyncLyricsToVideoKey, defaultValue = true)
    val (dontSkipOnFailure, onDontSkipOnFailureChange) = rememberPreference(DontSkipOnFailureKey, defaultValue = false)
    val (failureTimeout, onFailureTimeoutChange) = rememberPreference(StreamFailureTimeoutKey, defaultValue = 6)
    val (skipSilence, onSkipSilenceChange) = rememberPreference(SkipSilenceKey, defaultValue = false)
    val (sponsorBlock, onSponsorBlockChange) = rememberPreference(SponsorBlockEnabledKey, defaultValue = false)
    val (sbSponsor, onSbSponsor) = rememberPreference(SponsorBlockCategories[0].key, defaultValue = SponsorBlockCategories[0].defaultOn)
    val (sbSelfPromo, onSbSelfPromo) = rememberPreference(SponsorBlockCategories[1].key, defaultValue = SponsorBlockCategories[1].defaultOn)
    val (sbInteraction, onSbInteraction) = rememberPreference(SponsorBlockCategories[2].key, defaultValue = SponsorBlockCategories[2].defaultOn)
    val (sbIntroOutro, onSbIntroOutro) = rememberPreference(SponsorBlockCategories[3].key, defaultValue = SponsorBlockCategories[3].defaultOn)
    val (sbNonMusic, onSbNonMusic) = rememberPreference(SponsorBlockCategories[4].key, defaultValue = SponsorBlockCategories[4].defaultOn)
    val (reportPlays, onReportPlaysChange) = rememberPreference(YtReportPlaysKey, defaultValue = true)
    return listOf(
        switchItem(
            key = "Albums from liked songs",
            title = stringResource(R.string.settings_albums_from_likes),
            description = stringResource(R.string.settings_albums_from_likes_desc),
            detail = stringResource(R.string.settings_albums_from_likes_detail),
            icon = painterResource(R.drawable.album),
            checked = albumsFromLikes,
            onCheckedChange = onAlbumsFromLikesChange,
        ),
        switchItem(
            key = "Local song auto metadata",
            title = stringResource(R.string.settings_local_auto_metadata),
            description = stringResource(R.string.settings_local_auto_metadata_desc),
            icon = painterResource(R.drawable.cached),
            checked = autoMetadata,
            onCheckedChange = onAutoMetadataChange,
        ),
        switchItem(
            key = "Pause search history",
            title = stringResource(R.string.pause_search_history),
            description = stringResource(R.string.settings_pause_search_history_desc),
            icon = painterResource(R.drawable.history),
            checked = pauseSearchHistory,
            onCheckedChange = onPauseSearchHistoryChange,
        ),
        switchItem(
            key = "SponsorBlock",
            title = stringResource(R.string.sponsorblock),
            description = stringResource(R.string.sponsorblock_desc),
            icon = painterResource(R.drawable.skip_next),
            checked = sponsorBlock,
            onCheckedChange = onSponsorBlockChange,
        ),
        switchItem(
            key = "SponsorBlock sponsors",
            title = stringResource(R.string.sponsorblock_sponsor),
            description = stringResource(R.string.sponsorblock_sponsor_desc),
            icon = painterResource(R.drawable.skip_next),
            checked = sbSponsor,
            onCheckedChange = onSbSponsor,
            subOptionOf = sponsorBlock,
        ),
        switchItem(
            key = "SponsorBlock self promotion",
            title = stringResource(R.string.sponsorblock_selfpromo),
            description = stringResource(R.string.sponsorblock_selfpromo_desc),
            icon = painterResource(R.drawable.skip_next),
            checked = sbSelfPromo,
            onCheckedChange = onSbSelfPromo,
            subOptionOf = sponsorBlock,
        ),
        switchItem(
            key = "SponsorBlock interaction reminders",
            title = stringResource(R.string.sponsorblock_interaction),
            description = stringResource(R.string.sponsorblock_interaction_desc),
            icon = painterResource(R.drawable.skip_next),
            checked = sbInteraction,
            onCheckedChange = onSbInteraction,
            subOptionOf = sponsorBlock,
        ),
        switchItem(
            key = "SponsorBlock intro outro",
            title = stringResource(R.string.sponsorblock_introoutro),
            description = stringResource(R.string.sponsorblock_introoutro_desc),
            icon = painterResource(R.drawable.skip_next),
            checked = sbIntroOutro,
            onCheckedChange = onSbIntroOutro,
            subOptionOf = sponsorBlock,
        ),
        switchItem(
            key = "SponsorBlock non-music",
            title = stringResource(R.string.sponsorblock_nonmusic),
            description = stringResource(R.string.sponsorblock_nonmusic_desc),
            icon = painterResource(R.drawable.skip_next),
            checked = sbNonMusic,
            onCheckedChange = onSbNonMusic,
            subOptionOf = sponsorBlock,
        ),
        // the same switch as sync's youtube history one, signed out it still counts the play
        switchItem(
            key = "Count plays on YouTube views",
            title = stringResource(R.string.settings_youtube_plays),
            description = stringResource(R.string.settings_youtube_plays_desc),
            detail = stringResource(R.string.settings_youtube_plays_detail),
            icon = painterResource(R.drawable.trending_up),
            checked = reportPlays,
            onCheckedChange = onReportPlaysChange,
        ),
    )
}

// lyrics normally go to whoever has the most features, this lets the user's own provider order decide instead
@Composable
private fun lyricsItems(): List<SettingsItem> {
    val (overridden, setOverridden) = rememberPreference(LyricsOrderOverrideKey, defaultValue = false)
    val (savedOrder, setSavedOrder) = rememberPreference(LyricsProviderOrderKey, defaultValue = "")
    val (preferred) = rememberEnumPreference(PreferredLyricsProviderKey, defaultValue = PreferredLyricsProvider.YOULYPLUS)
    val overlay = LocalZoomOutOverlayState.current
    // auto captions are only asked when nobody else has lyrics, so dragging them anywhere would change nothing
    val order = remember(savedOrder, preferred) {
        LyricsProviderRegistry.effectiveProviderOrder(savedOrder, preferred).filter { it != YouTubeCaptionsLyricsProvider.name }
    }
    val openSheet = {
        overlay.showLyricsProviderSortSheet(order) { sorted ->
            setSavedOrder(LyricsProviderRegistry.serializeProviderOrder(sorted))
            setOverridden(true)
        }
    }
    val title = stringResource(R.string.settings_lyrics_order_override)
    return listOf(
        // like larp mode: the row opens the sheet, turning the switch on asks for an order first, off just stops using it
        SettingsItem(
            title = { Text(title) },
            highlightKey = "Override lyrics sorting",
            searchText = title,
            descriptionText = if (overridden) {
                stringResource(R.string.settings_lyrics_order_override_on, LyricsProviderRegistry.getDisplayName(order.first()))
            } else {
                stringResource(R.string.settings_lyrics_order_override_desc)
            },
            detail = stringResource(R.string.settings_lyrics_order_override_detail),
            icon = rememberVectorPainter(Icons.AutoMirrored.Rounded.Sort),
            iconShape = CircleShape,
            onClick = openSheet,
            trailingContent = {
                AppSwitch(checked = overridden, onCheckedChange = { on -> if (on) openSheet() else setOverridden(false) })
            },
        ),
    )
}

// most of what used to sit here moved to its own page, these keep it a tap away for anyone who still looks here
@Composable
private fun relatedItems(navController: NavController): List<SettingsItem> = listOf(
    linkItem(
        key = "Playback page",
        title = stringResource(R.string.settings_playback_category),
        description = stringResource(R.string.settings_playback_link_desc),
        icon = painterResource(R.drawable.play),
        onClick = { navController.navigate(PlaybackSettingsRoute) },
    ),
    linkItem(
        key = "Now playing page",
        title = stringResource(R.string.settings_now_playing),
        description = stringResource(R.string.settings_now_playing_link_desc),
        icon = painterResource(R.drawable.lyrics),
        onClick = { navController.navigate(NowPlayingSettingsRoute) },
    ),
)
