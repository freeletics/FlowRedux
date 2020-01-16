package com.freeletics.flowredux.util

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders

inline fun <reified T : ViewModel> AppCompatActivity.viewModel(factory: ViewModelProvider.Factory) =
    ViewModelProviders.of(this, factory)[T::class.java]
