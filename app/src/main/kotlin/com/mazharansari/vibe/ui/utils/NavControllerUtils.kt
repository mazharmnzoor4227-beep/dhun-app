// NavControllerUtils.kt

package com.mazharansari.vibe.ui.utils

import androidx.navigation.NavController
import com.mazharansari.vibe.ui.screens.Screens

fun NavController.backToMain() {
    val mainRoutes = Screens.MainScreens.map { it.route }

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}
