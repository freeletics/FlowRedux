package com.freeletics.flowredux

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class PopularRepositoriesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragmentContainer, PopularRepositoriesFragment())
                .commit()
        }
    }
}

