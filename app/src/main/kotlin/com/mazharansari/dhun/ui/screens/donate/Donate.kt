package com.mazharansari.dhun.ui.screens.donate

import androidx.compose.runtime.Composable
import com.mazharansari.dhun.ui.component.PopupSheetState

// Dhun has no donation or support prompts; these stay so the rest of the app compiles unchanged.

/** nothing to show, Dhun asks for no support */
fun PopupSheetState.showSupportSheet() {}

/** nothing to show, Dhun asks for no donations */
fun PopupSheetState.showDonateSheet() {}

/** upstream's periodic donation prompt scheduler, intentionally empty */
@Composable
fun DonatePromptScheduler(enabled: Boolean) {}
