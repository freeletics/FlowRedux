package com.freeletics.flowredux.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun LoadingUi(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        CircularProgressIndicator(Modifier.align(Alignment.Center))
    }
}
