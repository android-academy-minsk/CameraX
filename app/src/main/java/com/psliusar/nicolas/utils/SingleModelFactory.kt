package com.psliusar.nicolas.utils

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner

class SingleModelFactory<M : ViewModel>(
    private val singleClass: Class<M>,
    private val creator: () -> M
) : ViewModelProvider.Factory {

    companion object {

        inline fun <reified M : ViewModel> getFromActivity(fragment: Fragment): M =
            get(fragment.requireActivity() as ViewModelStoreOwner) {
                throw IllegalStateException("ViewModel must be instantiated already")
                null as M
            }

        inline fun <reified M : ViewModel> get(storeOwner: ViewModelStoreOwner, noinline creator: () -> M): M =
            ViewModelProvider(storeOwner, SingleModelFactory(M::class.java, creator)).get(M::class.java)
    }

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass != singleClass) {
            throw IllegalArgumentException("Unsupported class $modelClass")
        }
        return creator() as T
    }
}