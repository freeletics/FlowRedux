package com.freeletics.flowredux.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class ComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PopularRepositoriesUi()
        }
    }
}