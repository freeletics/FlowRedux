package com.freeletics.flowredux.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import com.freeletics.flowredux.sample.shared.GithubApi
import com.freeletics.flowredux.sample.shared.InternalPaginationStateMachine

internal class ComposeViewModel : ViewModel() {
    internal val stateMachine = InternalPaginationStateMachine(githubApi = GithubApi())
}

class ComposeActivity : ComponentActivity() {

    private val vm by viewModels<ComposeViewModel>()
    private val stateMachine get() = vm.stateMachine

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
