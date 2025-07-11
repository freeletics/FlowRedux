package com.freeletics.flowredux2

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import com.freeletics.flowredux2.compose.ComposeActivity
import com.freeletics.flowredux2.sample.android.R
import com.freeletics.flowredux2.traditional.TraditionalPopularRepositoriesActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.traditional).setOnClickListener {
            startActivity(
                Intent(
                    this@MainActivity,
                    TraditionalPopularRepositoriesActivity::class.java,
                ),
            )
        }

        findViewById<View>(R.id.compose).setOnClickListener {
            startActivity(Intent(this@MainActivity, ComposeActivity::class.java))
        }
    }
}
