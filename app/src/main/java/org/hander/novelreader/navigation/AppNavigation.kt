package org.hander.novelreader.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import org.hander.novelreader.reader.ReaderViewModel
import org.hander.novelreader.ui.HanderTopBar
import org.hander.novelreader.ui.HomeScreen
import org.hander.novelreader.ui.LibrariesScreen
import org.hander.novelreader.ui.MainViewModel
import org.hander.novelreader.ui.OfflineScreen
import org.hander.novelreader.ui.ReaderScreen
import org.hander.novelreader.ui.SettingsScreen
import org.hander.novelreader.ui.NovelScreen

import org.hander.novelreader.ui.theme.HanderBackground
import org.hander.novelreader.ui.theme.HanderBorder
import org.hander.novelreader.ui.theme.HanderGold
import org.hander.novelreader.ui.theme.HanderIvory
import org.hander.novelreader.ui.theme.HanderMuted
import org.hander.novelreader.ui.theme.HanderPanel
import org.hander.novelreader.ui.theme.HanderTealDeep

object Routes {
    const val HOME = "home"
    const val LIBRARIES = "libraries"
    const val OFFLINE = "offline"
    const val SETTINGS = "settings"
    const val READER = "reader"
}

private enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Home(
        Routes.HOME,
        "Home",
        Icons.Outlined.Home
    ),

    Libraries(
        Routes.LIBRARIES,
        "Libraries",
        Icons.Outlined.LocalLibrary
    ),

    Offline(
        Routes.OFFLINE,
        "Offline",
        Icons.Outlined.FolderOpen
    ),

    Settings(
        Routes.SETTINGS,
        "Settings",
        Icons.Outlined.Settings
    )
}

@Composable
fun HanderRoot(
    mainVm: MainViewModel,
    readerVm: ReaderViewModel
) {
    val nav = rememberNavController()

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val inReader = route == Routes.READER

    val library by mainVm.library.collectAsStateWithLifecycle()
    val settings by mainVm.settings.collectAsStateWithLifecycle()
    val online by mainVm.online.collectAsStateWithLifecycle()
    val voices by mainVm.voices.collectAsStateWithLifecycle()
    val files by mainVm.files.collectAsStateWithLifecycle()
    val scanning by mainVm.scanning.collectAsStateWithLifecycle()
    val readerUi by readerVm.ui.collectAsStateWithLifecycle()

    fun openBook(
        uri: String,
        title: String
    ) {
        readerVm.open(uri, title)
        nav.navigate(Routes.READER)
    }

    Scaffold(
        containerColor = HanderBackground,

        topBar = {
            if (!inReader) {
                HanderTopBar(online)
            }
        },

        bottomBar = {
            if (!inReader) {
                HanderBottomBar(route) {
                    navigateTo(nav, it)
                }
            }
        }
    ) { padding ->

        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {

            composable(Routes.HOME) {
                HomeScreen(
                    library = library,

                    onOpen = {
                        openBook(
                            it.uri,
                            it.title
                        )
                    },

                    onBrowseLibraries = {
                        navigateTo(
                            nav,
                            Routes.LIBRARIES
                        )
                    },

                    onOpenOffline = {
                        navigateTo(
                            nav,
                            Routes.OFFLINE
                        )
                    }
                )
            }

            composable(Routes.LIBRARIES) {
                LibrariesScreen(
                    onSelectNovel = { novel ->
                        nav.navigate("novel/${novel.sourceId}/${Uri.encode(novel.id)}")
                    }
                )
            }

            composable(Routes.OFFLINE) {
                OfflineScreen(
                    library = library,
                    files = files,
                    scanning = scanning,

                    onFolderPicked = mainVm::onFolderPicked,

                    onRescan = mainVm::rescan,

                    onOpenPdf = {
                        openBook(
                            it.uri.toString(),
                            it.title
                        )
                    },

                    onToggleFavorite = {
                        mainVm.toggleFavorite(
                            it.uri,
                            it.title
                        )
                    }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings = settings,
                    voices = voices,
                    library = library,

                    onChange = mainVm::updateSettings,

                    onPreviewVoice = {
                        mainVm.previewVoice(
                            "The lantern swung once, twice, and the corridor ahead swallowed its light."
                        )
                    },

                    onFolderPicked = mainVm::onFolderPicked,

                    onClearRecents = mainVm::clearRecents
                )
            }

            composable(Routes.READER) {
                ReaderScreen(
                    vm = readerVm,
                    settings = settings,

                    isFavorite =
                        library.books[readerUi.bookUri]?.favorite == true,

                    onToggleFavorite = {
                        mainVm.toggleFavorite(
                            readerUi.bookUri,
                            readerUi.title
                        )
                    },

                    onChangeSettings = mainVm::updateSettings,

                    onBack = {
                        readerVm.stopSpeech()
                        nav.popBackStack()
                    }
                )
            }

            composable("novel/{sourceId}/{novelId}") { backEntry ->
                val sourceId = backEntry.arguments?.getString("sourceId") ?: ""
                val novelIdRaw = backEntry.arguments?.getString("novelId") ?: ""
                val novelId = Uri.decode(novelIdRaw)
                NovelScreen(nav, sourceId, novelId)
            }
        }
    }
}

private fun navigateTo(
    nav: NavHostController,
    route: String
) {
    nav.navigate(route) {
        popUpTo(
            nav.graph.findStartDestination().id
        ) {
            saveState = true
        }

        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun HanderBottomBar(
    current: String?,
    onNavigate: (String) -> Unit
) {
    Column(
        Modifier.background(HanderPanel)
    ) {

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HanderBorder)
        )

        NavigationBar(
            containerColor = HanderPanel,
            contentColor = HanderIvory
        ) {

            Destination.entries.forEach { destination ->

                NavigationBarItem(
                    selected = current == destination.route,

                    onClick = {
                        onNavigate(destination.route)
                    },

                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.label
                        )
                    },

                    label = {
                        Text(
                            text = destination.label,
                            fontSize = 11.sp
                        )
                    },

                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = HanderGold,
                        selectedTextColor = HanderGold,
                        indicatorColor = HanderTealDeep,
                        unselectedIconColor = HanderMuted,
                        unselectedTextColor = HanderMuted
                    )
                )
            }
        }
    }
}
