package com.rst.player.ui.core

import androidx.compose.runtime.staticCompositionLocalOf
import com.rst.player.AppGraph

val LocalGraph = staticCompositionLocalOf<AppGraph> {
    error("AppGraph not provided")
}
