package com.katok.pro.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.katok.pro.R
import com.katok.pro.databinding.FragmentAdminBinding
import com.katok.pro.ui.monitoring.MonitoringFragment
import com.katok.pro.util.SessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminFragment : Fragment() {

    private var _binding: FragmentAdminBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private var isAdminOrModerator = false
    private var isAdvertiser = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionManager = SessionManager(requireContext())

        lifecycleScope.launch {
            val role = sessionManager.getUserRole()
            isAdminOrModerator = role == "ADMIN" || role == "MODERATOR"
            isAdvertiser = role == "ADVERT"

            setupViewPager()
        }
    }

    private fun setupViewPager() {
        val fragments = mutableListOf<Fragment>()
        val titles = mutableListOf<String>()

        // Всегда добавляем вкладку "Реклама"
        fragments.add(AdminAdvertisingFragment())
        titles.add("Реклама")

        // Для ADMIN/MODERATOR добавляем остальные вкладки
        if (isAdminOrModerator) {
            fragments.add(AdminUserFragment())
            titles.add("Пользователи")
            fragments.add(AdminAdsFragment())
            titles.add("Объявления")
            fragments.add(AdminMessagesFragment())
            titles.add("Сообщения")
            fragments.add(MonitoringFragment())
            titles.add("Мониторинг")
        }

        val adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = titles[position]
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}