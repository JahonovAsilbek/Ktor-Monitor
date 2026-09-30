package uz.jahonov.ktormonitor.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore

/**
 * Clears view models at the end of a test, as the platform would. Without it a view model keeps
 * collecting after the test and touches Dispatchers.Main once it has been reset.
 */
class TestViewModels {
    private val store = ViewModelStore()

    fun <T : ViewModel> track(viewModel: T): T = viewModel.also { store.put(it.toString(), it) }

    fun clear() = store.clear()
}
