package com.rst.player.ui.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * Shared-element (hero) bridge between the mini player artwork and the full
 * Now Playing artwork. The mini player continuously reports its art bounds
 * (in root coordinates); Now Playing morphs its artwork from those bounds up
 * to its own layout bounds and back on close.
 */
class PlayerHeroState {
    var miniArtBounds by mutableStateOf(Rect.Zero)
}
