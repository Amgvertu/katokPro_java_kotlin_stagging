package com.katok.pro.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.katok.pro.MainActivity
import com.katok.pro.R
import com.katok.pro.adapter.ProfilePagerAdapter
import com.katok.pro.databinding.FragmentProfileBinding
import com.katok.pro.services.WebSocketForegroundService
import com.katok.pro.util.ProfileCacheManager
import com.katok.pro.util.SessionManager
import com.katok.pro.util.TokenManager
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var pagerAdapter: ProfilePagerAdapter
    private lateinit var sessionManager: SessionManager
    private lateinit var tokenManager: TokenManager
    private lateinit var profileViewModel: ProfileViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())
        lifecycleScope.launch {
            val isLoggedIn = sessionManager.isLoggedIn()
            Log.d("ProfileFragment", "onViewCreated, isLoggedIn: $isLoggedIn")
        }
        tokenManager = TokenManager.getInstance(requireContext())

        setupToolbar()

        // Инициализация адаптера выполняется в onResume, но для первого запуска вызываем здесь
        setupViewPager()

        binding.tabLayout.setTabTextColors(
            resources.getColor(android.R.color.white, null),
            resources.getColor(R.color.accent, null)
        )
        binding.tabLayout.setSelectedTabIndicatorColor(resources.getColor(R.color.accent, null))

        profileViewModel = ViewModelProvider(requireActivity()).get(ProfileViewModel::class.java)

        // Наблюдаем за позицией из ViewModel
        profileViewModel.getTabPosition().observe(viewLifecycleOwner) { position ->
            position?.let {
                if (binding.viewPager.currentItem != it) {
                    binding.viewPager.setCurrentItem(it, false)
                    binding.tabLayout.getTabAt(it)?.select()
                }
            }
        }

        // Слушатель выбора вкладок – сохраняем позицию
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    val position = it.position
                    profileViewModel.setTabPosition(position)
                    // Синхронизируем ViewPager (на случай, если TabLayout изменился)
                    if (binding.viewPager.currentItem != position) {
                        binding.viewPager.setCurrentItem(position, false)
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Если есть сохранённая позиция – применяем её
        val savedPosition = profileViewModel.getTabPosition().value ?: 0
        binding.viewPager.setCurrentItem(savedPosition, false)
        binding.tabLayout.getTabAt(savedPosition)?.select()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Профиль"
        binding.toolbar.setTitleTextColor(resources.getColor(android.R.color.white, null))
        binding.toolbar.inflateMenu(R.menu.profile_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_logout) {
                logout()
                true
            } else false
        }
    }

    private fun setupViewPager() {
        pagerAdapter = ProfilePagerAdapter(requireActivity())
        pagerAdapter.addFragment(ProfileInfoFragment(), "Профиль")
        pagerAdapter.addFragment(NotificationSettingsFragment(), "Уведомления")
        pagerAdapter.addFragment(ProfileSettingsFragment(), "Настройки")

        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.isSaveEnabled = false // отключаем восстановление состояния

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = pagerAdapter.getTitle(position)
        }.attach()
    }

    private fun logout() {
        AlertDialog.Builder(requireContext())
            .setTitle("Выход")
            .setMessage("Вы уверены, что хотите выйти?")
            .setPositiveButton("Да") { _, _ ->
                lifecycleScope.launch {
                    sessionManager.logout()
                    tokenManager.clear()
                    ProfileCacheManager(requireContext()).clear()
                    val profileInfoViewModel = ViewModelProvider(requireActivity())
                        .get(ProfileInfoViewModel::class.java)
                    profileInfoViewModel.clearAll()
                    NavHostFragment.findNavController(this@ProfileFragment).navigate(R.id.loginFragment)
                    WebSocketForegroundService.stop(requireContext())
                    Toast.makeText(context, "Вы вышли из системы", Toast.LENGTH_SHORT).show()

                    // Обновляем меню в MainActivity
                    (requireActivity() as? MainActivity)?.resetTokenCheck()
                    (requireActivity() as? MainActivity)?.refreshMenu()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        val savedPosition = profileViewModel.getTabPosition().value ?: 0

        // Пересоздаём адаптер, чтобы полностью сбросить внутреннее состояние ViewPager2
        setupViewPager()

        // Устанавливаем сохранённую позицию и синхронизируем TabLayout
        binding.viewPager.setCurrentItem(savedPosition, false)
        binding.tabLayout.getTabAt(savedPosition)?.select()

        // Дополнительно обновляем ViewModel, чтобы позиция была актуальной
        profileViewModel.setTabPosition(savedPosition)
    }
}