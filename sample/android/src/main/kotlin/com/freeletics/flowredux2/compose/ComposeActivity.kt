package com.freeletics.flowredux2.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.freeletics.flowredux2.produceStateMachine
import com.freeletics.flowredux2.sample.shared.GithubApi
import com.freeletics.flowredux2.sample.shared.InternalPaginationStateMachineFactory

class ComposeActivity : ComponentActivity() {
    private val factory = InternalPaginationStateMachineFactory(githubApi = GithubApi())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val stateMachine = factory.produceStateMachine()
            PopularRepositoriesUi(
                modifier = Modifier.fillMaxSize(),
                state = stateMachine.state.value,
                dispatch = stateMachine.dispatchAction,
            )
        }
    }
}
