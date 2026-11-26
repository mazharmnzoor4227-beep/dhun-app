// LibraryViewModels.kt

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.mazharansari.vibe.viewmodels

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.mazharansari.vibe.constants.AlbumFilter
import com.mazharansari.vibe.constants.AlbumFilterKey
import com.mazharansari.vibe.constants.AlbumSortDescendingKey
import com.mazharansari.vibe.constants.AlbumSortType
import com.mazharansari.vibe.constants.AlbumSortTypeKey
import com.mazharansari.vibe.constants.ArtistFilter
import com.mazharansari.vibe.constants.ArtistFilterKey
import com.mazharansari.vibe.constants.ArtistSongSortDescendingKey
import com.mazharansari.vibe.constants.ArtistSongSortType
import com.mazharansari.vibe.constants.ArtistSongSortTypeKey
import com.mazharansari.vibe.constants.ArtistSortDescendingKey
import com.mazharansari.vibe.constants.ArtistSortType
import com.mazharansari.vibe.constants.ArtistSortTypeKey
import com.mazharansari.vibe.constants.HideExplicitKey
import com.mazharansari.vibe.constants.HideVideoSongsKey
import com.mazharansari.vibe.constants.HideYoutubeShortsKey
import com.mazharansari.vibe.constants.LibraryFilter
import com.mazharansari.vibe.constants.PlaylistSortDescendingKey
import com.mazharansari.vibe.constants.PlaylistSortType
import com.mazharansari.vibe.constants.PlaylistSortTypeKey
import com.mazharansari.vibe.constants.SongFilter
import com.mazharansari.vibe.constants.SongFilterKey
import com.mazharansari.vibe.constants.SongSortDescendingKey
import com.mazharansari.vibe.constants.SongSortType
import com.mazharansari.vibe.constants.SongSortTypeKey
import com.mazharansari.vibe.constants.TopSize
import com.mazharansari.vibe.db.MusicDatabase
import com.mazharansari.vibe.extensions.filterExplicit
import com.mazharansari.vibe.extensions.filterExplicitAlbums
import com.mazharansari.vibe.extensions.filterVideoSongs
import com.mazharansari.vibe.extensions.filterYoutubeShorts
import com.mazharansari.vibe.extensions.toEnum
import com.mazharansari.vibe.playback.DownloadUtil
import com.mazharansari.vibe.utils.ArtistImageResolver
import com.mazharansari.vibe.utils.SyncUtils
import com.mazharansari.vibe.utils.dataStore
import com.mazharansari.vibe.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class LibrarySongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allSongs =
        context.dataStore.data
            .map {
                Triple(
                    Triple(
                        it[SongFilterKey].toEnum(SongFilter.LIKED),
                        it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                        (it[SongSortDescendingKey] ?: true),
                    ),
                    it[HideExplicitKey] ?: false,
                    it[HideVideoSongsKey] ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, hideExplicit, hideVideoSongs) ->
                val (filter, sortType, descending) = filterSort
                when (filter) {
                    SongFilter.LIBRARY -> database.songs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.LIKED -> database.likedSongs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.DOWNLOADED -> database.downloadedSongs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.UPLOADED -> database.uploadedSongs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }

    fun syncLibrarySongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLibrarySongs() }
    }

    fun syncUploadedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncUploadedSongs() }
    }
}

@HiltViewModel
class LibraryArtistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allArtists =
        context.dataStore.data
            .map {
                Triple(
                    it[ArtistFilterKey].toEnum(ArtistFilter.LIKED),
                    it[ArtistSortTypeKey].toEnum(ArtistSortType.CREATE_DATE),
                    it[ArtistSortDescendingKey] ?: true,
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filter, sortType, descending) ->
                when (filter) {
                    ArtistFilter.LIKED -> database.artistsBookmarked(sortType, descending)
                    ArtistFilter.LIBRARY -> database.artists(sortType, descending)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncArtistsSubscriptions() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // refresh stale thumbnails once instead of pinning the room flow forever in the background
            val artists = allArtists.first { it.isNotEmpty() }
            artists
                .map { it.artist }
                .filter {
                    it.thumbnailUrl == null || Duration.between(
                        it.lastUpdateTime,
                        LocalDateTime.now()
                    ) > Duration.ofDays(10)
                }.forEach { artist ->
                    val preferredThumbnailUrl = ArtistImageResolver.resolveThumbnail(artist)
                    YouTube.artist(artist.id).onSuccess { artistPage ->
                        database.query {
                            update(artist, artistPage, preferredThumbnailUrl)
                        }
                    }.onFailure {
                        if (preferredThumbnailUrl != null) {
                            database.query {
                                update(
                                    artist.copy(
                                        thumbnailUrl = preferredThumbnailUrl,
                                        lastUpdateTime = LocalDateTime.now()
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }
}

@HiltViewModel
class LibraryAlbumsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allAlbums =
        context.dataStore.data
            .map {
                Pair(
                    Triple(
                        it[AlbumFilterKey].toEnum(AlbumFilter.LIKED),
                        it[AlbumSortTypeKey].toEnum(AlbumSortType.CREATE_DATE),
                        it[AlbumSortDescendingKey] ?: true,
                    ),
                    it[HideExplicitKey] ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, hideExplicit) ->
                val (filter, sortType, descending) = filterSort
                when (filter) {
                    AlbumFilter.LIKED -> database.albumsLiked(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                    AlbumFilter.LIBRARY -> database.albums(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                    AlbumFilter.UPLOADED -> database.albumsUploaded(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedAlbums() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // backfill empty albums once instead of holding an eternal room subscriber in the background
            val albums = allAlbums.first { it.isNotEmpty() }
            albums
                .filter {
                    it.album.songCount == 0
                }.forEach { album ->
                    YouTube
                        .album(album.id)
                        .onSuccess { albumPage ->
                            database.query {
                                update(album.album, albumPage, album.artists)
                            }
                        }.onFailure {
                            reportException(it)
                            if (it.message?.contains("NOT_FOUND") == true) {
                                database.query {
                                    delete(album.album)
                                }
                            }
                        }
                }
        }
    }
}

@HiltViewModel
class LibraryPlaylistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allPlaylists =
        context.dataStore.data
            .map {
                Triple(
                    it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE),
                    it[PlaylistSortDescendingKey] ?: true,
                    it[HideYoutubeShortsKey] ?: true
                )
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending, hideYoutubeShorts) ->
                database.playlists(sortType, descending).map { it.filterYoutubeShorts(hideYoutubeShorts) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncSavedPlaylists() }
    }

    val topValue =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()
}

@HiltViewModel
class ArtistSongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId = savedStateHandle.get<String>("artistId")!!
    val artist =
        database
            .artist(artistId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val songs =
        context.dataStore.data
            .map {
                Triple(
                    it[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE) to (it[ArtistSongSortDescendingKey]
                        ?: true),
                    it[HideExplicitKey] ?: false,
                    it[HideVideoSongsKey] ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (sortDesc, hideExplicit, hideVideoSongs) ->
                val (sortType, descending) = sortDesc
                database.artistSongs(artistId, sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@HiltViewModel
class LibraryMixViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val syncAllLibrary = {
         viewModelScope.launch(Dispatchers.IO) {
             syncUtils.tryAutoSync()
         }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            syncUtils.performFullSyncSuspend()
            _isRefreshing.value = false
        }
    }

    val topValue =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()
    var artists =
        database
            .artistsBookmarked(
                ArtistSortType.CREATE_DATE,
                true,
            ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var albums = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.albumsLiked(AlbumSortType.CREATE_DATE, true).map { it.filterExplicitAlbums(hideExplicit) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    var playlists = context.dataStore.data
        .map { it[HideYoutubeShortsKey] ?: true }
        .distinctUntilChanged()
        .flatMapLatest { hideYoutubeShorts ->
            database.playlists(PlaylistSortType.CREATE_DATE, true).map { it.filterYoutubeShorts(hideYoutubeShorts) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val loadedAlbums = albums.first { it.isNotEmpty() }
            loadedAlbums
                .filter {
                    it.album.songCount == 0
                }.forEach { album ->
                    YouTube
                        .album(album.id)
                        .onSuccess { albumPage ->
                            database.query {
                                update(album.album, albumPage, album.artists)
                            }
                        }.onFailure {
                            reportException(it)
                            if (it.message?.contains("NOT_FOUND") == true) {
                                database.query {
                                    delete(album.album)
                                }
                            }
                        }
                }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val loadedArtists = artists.first { it.isNotEmpty() }
            loadedArtists
                .map { it.artist }
                .filter {
                    it.thumbnailUrl == null ||
                            Duration.between(
                                it.lastUpdateTime,
                                LocalDateTime.now(),
                            ) > Duration.ofDays(10)
                }.forEach { artist ->
                    val preferredThumbnailUrl = ArtistImageResolver.resolveThumbnail(artist)
                    YouTube.artist(artist.id).onSuccess { artistPage ->
                        database.query {
                            update(artist, artistPage, preferredThumbnailUrl)
                        }
                    }.onFailure {
                        if (preferredThumbnailUrl != null) {
                            database.query {
                                update(
                                    artist.copy(
                                        thumbnailUrl = preferredThumbnailUrl,
                                        lastUpdateTime = LocalDateTime.now(),
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }
}

@HiltViewModel
class LibraryViewModel
@Inject
constructor() : ViewModel() {
    private val curScreen = mutableStateOf(LibraryFilter.LIBRARY)
    val filter: MutableState<LibraryFilter> = curScreen
}
