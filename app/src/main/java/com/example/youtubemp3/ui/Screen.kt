package com.vusal.soundra.ui

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Home : Screen("home")
    object Search : Screen("search")
    object Playlists : Screen("playlists")
    object PlaylistDetail : Screen("playlist_detail")
    object Downloads : Screen("downloads")
    object Settings : Screen("settings")
    object About : Screen("about")
}
