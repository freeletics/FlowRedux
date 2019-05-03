package com.freeletics.rxredux

import android.view.ViewGroup


typealias ViewBindingInstantiator = (ViewGroup) -> Any
typealias ViewBindingInstantiatorMap = Map<Class<*>, ViewBindingInstantiator>

@Suppress("UNCHECKED_CAST")
class ViewBindingFactory(
    private val instantiatorMap: ViewBindingInstantiatorMap
) {

    /**
     * creates a new ViewBinding
     */
    fun <T> create(key: Class<*>, rootView: ViewGroup) = instantiatorMap[key]!!(rootView) as T
}
