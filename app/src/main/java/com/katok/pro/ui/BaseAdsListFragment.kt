package com.katok.pro.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.katok.pro.R
import com.katok.pro.adapter.AdCardAdapter
import com.katok.pro.model.Ad
import com.katok.pro.model.Rink
import com.katok.pro.util.ToastHelper
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

abstract class BaseAdsListFragment : Fragment() { // Не наследуем BaseFragment, т.к. layout создаётся в наследниках

    protected lateinit var recyclerView: RecyclerView
    protected lateinit var swipeRefresh: SwipeRefreshLayout
    protected lateinit var progressBar: ProgressBar
    protected lateinit var tvEmpty: TextView

    protected lateinit var adapter: AdCardAdapter

    // Абстрактные свойства – наследник должен их предоставить
    protected abstract val adsFlow: StateFlow<List<Ad>>
    protected abstract val isLoadingFlow: StateFlow<Boolean>
    protected abstract val errorFlow: StateFlow<String?>
    protected abstract val emptyMessageFlow: StateFlow<String?>
    protected abstract val rinkListFlow: StateFlow<List<Rink>>

    protected abstract fun onRefresh()  // вызывается при свайпе
    protected abstract fun createAdActionListener(): AdCardAdapter.OnAdActionListener

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmpty = view.findViewById(R.id.tvEmpty)

        setupRecyclerView()
        setupObservers()
        setupSwipeRefresh()
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val initialRinks = rinkListFlow.value ?: emptyList()
        adapter = AdCardAdapter(initialRinks, createAdActionListener(), requireContext())
        recyclerView.adapter = adapter
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            adsFlow.collect { ads ->
                Log.d("BaseAdsListFragment", "🔥 adsFlow collected, size=${ads.size}")
                adapter.submitList(ads)
                updateEmptyView(ads.isEmpty())
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            isLoadingFlow.collect { isLoading ->
                if (!isAdded || progressBar == null) return@collect
                progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                swipeRefresh.isRefreshing = isLoading
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            errorFlow.collect { error ->
                if (!isAdded) return@collect
                if (error != null) {
                    // Если ошибка 401 – переход на логин
                    if (error == "error_unauthorized" || error.contains("авторизация")) {
                        findNavController().navigate(R.id.loginFragment)
                    } else {
                        ToastHelper.showError(requireContext(), error)
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            rinkListFlow.collect { rinks ->
                if (!isAdded || !::adapter.isInitialized) return@collect
                adapter.updateRinks(rinks)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener { onRefresh() }
    }

    protected fun updateEmptyView(isEmpty: Boolean) {
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        if (isEmpty) {
            tvEmpty.text = emptyMessageFlow.value ?: "Нет объявлений"
        }
    }
}