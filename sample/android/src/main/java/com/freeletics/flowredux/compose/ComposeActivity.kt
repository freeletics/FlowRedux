package com.freeletics.flowredux.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.freeletics.flowredux.AndroidFlowReduxLogger
import com.freeletics.flowredux.sample.shared.GithubApi
import com.freeletics.flowredux.sample.shared.InternalPaginationStateMachine

class ComposeActivity : ComponentActivity() {

    private val stateMachine = InternalPaginationStateMachine(
        githubApi = GithubApi(),
        logger = AndroidFlowReduxLogger
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val (state, dispatch) = stateMachine.rememberStateAndDispatch()
            PopularRepositoriesUi(state.value, dispatch)
        }
    }
}