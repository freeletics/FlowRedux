package com.freeletics.flowredux

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.freeletics.flowredux.sample.shared.Greeting
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.error

class PopularRepositoriesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        error.text = Greeting.hello()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

