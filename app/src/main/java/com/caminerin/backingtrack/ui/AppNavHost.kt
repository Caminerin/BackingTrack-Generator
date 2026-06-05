package com.caminerin.backingtrack.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.caminerin.backingtrack.ui.screens.CreateTrackScreen
import com.caminerin.backingtrack.ui.screens.HomeScreen
import com.caminerin.backingtrack.ui.screens.LibraryScreen
import com.caminerin.backingtrack.ui.screens.PlayerScreen
import com.caminerin.backingtrack.ui.screens.ProgressionBuilderScreen

object Routes {
    const val HOME = "home"
    const val CREATE = "create"
    const val PLAYER = "player"
    const val PROGRESSION = "progression"
    const val LIBRARY = "library"
}

@Composable
fun AppNavHost(vm: AppViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(vm, nav) }
        composable(Routes.CREATE) { CreateTrackScreen(vm, nav) }
        composable(Routes.PLAYER) { PlayerScreen(vm, nav) }
        composable(Routes.PROGRESSION) { ProgressionBuilderScreen(vm, nav) }
        composable(Routes.LIBRARY) { LibraryScreen(vm, nav) }
    }
}
