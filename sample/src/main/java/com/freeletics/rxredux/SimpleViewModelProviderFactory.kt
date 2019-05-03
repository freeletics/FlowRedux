package com.freeletics.rxredux

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import javax.inject.Provider

class SimpleViewModelProviderFactory<T : ViewModel>(
    private val provider: Provider<T>) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel?> create(modelClass: Class<T>): T = provider.get() as T
}
