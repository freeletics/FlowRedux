package com.freeletics.flowredux.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.freeletics.flowredux.sample.shared.GithubApi
import com.freeletics.flowredux.sample.shared.InternalPaginationStateMachine

class ComposeActivity : ComponentActivity() {

    private val stateMachine = InternalPaginationStateMachine(githubApi = GithubApi())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val (state, dispatch) = stateMachine.rememberStateAndDispatch()

            PopularRepositoriesUi(
                modifier = Modifier.fillMaxSize(),
                state = state.value,
                dispatch = dispatch,
            )
        }
    }
}
