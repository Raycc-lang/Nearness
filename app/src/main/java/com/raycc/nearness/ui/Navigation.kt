package com.raycc.nearness.ui

import kotlinx.serialization.Serializable

@Serializable
sealed interface Screen {
    @Serializable
    data object Auth : Screen

    @Serializable
    data object Pairing : Screen

    @Serializable
    data object Home : Screen

    @Serializable
    data object Whiteboard : Screen

    @Serializable
    data object Archive : Screen
}
