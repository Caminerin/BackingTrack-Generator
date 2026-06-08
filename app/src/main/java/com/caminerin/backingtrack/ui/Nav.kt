package com.caminerin.backingtrack.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.caminerin.backingtrack.ui.screens.CatalogScreen
import com.caminerin.backingtrack.ui.screens.PlayerScreen

object Routes {
    const val CATALOG = "catalog"
    const val PLAYER = "player"
}

@Composable
fun AppNav(vm: MainViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.CATALOG) {
        composable(Routes.CATALOG) { CatalogScreen(vm, nav) }
        composable(Routes.PLAYER) { PlayerScreen(vm, nav) }
    }
}
