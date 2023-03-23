package com.freeletics.flowredux.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.freeletics.flowredux.sample.android.R
import com.freeletics.flowredux.sample.shared.Action
import com.freeletics.flowredux.sample.shared.RetryLoadingFirstPage

@Composable
fun ErrorUi(modifier: Modifier, dispatch: (Action) -> Unit) {
    Box(modifier) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .wrapContentSize()
                .clickable { dispatch(RetryLoadingFirstPage) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = "Error",
            )
            Text(
                textAlign = TextAlign.Center,
                text = stringResource(R.string.unexpected_error_retry),
            )
        }
    }
}
