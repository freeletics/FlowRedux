package com.freeletics.flowredux.util

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.arch.lifecycle.ViewModelProviders
import android.support.v4.app.FragmentActivity

inline fun <reified T : ViewModel> FragmentActivity.viewModel(factory: ViewModelProvider.Factory)
    = ViewModelProviders.of(this, factory)[T::class.java]
