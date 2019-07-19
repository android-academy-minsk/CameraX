package com.psliusar.nicolas.utils

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import java.lang.IllegalStateException

class SingleModelFactory<M : ViewModel>(
    private val singleClass: Class<M>,
    private val creator: () -> M
) : ViewModelProvider.Factory {

    companion object {

        inline fun <reified M : ViewModel> getFromActivity(fragment: Fragment): M =
            get(fragment.requireActivity(), M::class.java) {
                throw IllegalStateException("ViewModel must be instantiated already")
            }

        inline fun <reified M : ViewModel> get(activity: FragmentActivity, noinline creator: () -> M): M =
            get(activity, M::class.java, creator)

        fun <M : ViewModel> get(activity: FragmentActivity, singleClass: Class<M>, creator: () -> M): M =
            ViewModelProviders.of(activity, SingleModelFactory(singleClass, creator)).get(singleClass)

        inline fun <reified M : ViewModel> get(fragment: Fragment, noinline creator: () -> M): M =
            get(fragment, M::class.java, creator)

        fun <M : ViewModel> get(fragment: Fragment, singleClass: Class<M>, creator: () -> M): M =
            ViewModelProviders.of(fragment, SingleModelFactory(singleClass, creator)).get(singleClass)
    }

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass != singleClass) {
            throw IllegalArgumentException("Unsupported class $modelClass")
        }
        return creator() as T
    }
}