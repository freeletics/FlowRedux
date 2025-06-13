package com.freeletics.flowredux2.compose.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.freeletics.flowredux2.sample.android.R
import com.freeletics.flowredux2.sample.shared.Action
import com.freeletics.flowredux2.sample.shared.RetryLoadingFirstPage

@Composable
internal fun ErrorUi(
    dispatch: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
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
