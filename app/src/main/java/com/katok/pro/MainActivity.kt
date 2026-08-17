package com.katok.pro

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph
import androidx.navigation.NavInflater
import androidx.navigation.NavOptions
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.katok.pro.databinding.ActivityMainBinding
import com.katok.pro.network.ApiClient
import com.katok.pro.repository.UserRepository
import com.katok.pro.services.WebSocketForegroundService
import com.katok.pro.ui.MainActivityViewModel
import com.katok.pro.ui.MessagesViewModel
import com.katok.pro.util.NotificationHelper
import com.katok.pro.util.PrivacyHelper
import com.katok.pro.util.ProfileHelper
import com.katok.pro.util.SessionManager
import com.katok.pro.util.TokenManager
import com.katok.pro.util.TokenRegistrationService
import com.katok.pro.workers.TokenRefreshScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var badgeView: TextView
    private lateinit var binding: ActivityMainBinding
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var sessionManager: SessionManager
    private lateinit var tokenManager: TokenManager
    private var authListener: ApiClient.AuthListener? = null
    private var isTokenChecked = false

    private val mainViewModel: MainActivityViewModel by viewModels()
    private var optionsMenu: Menu? = null

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1002
        private const val TAG = "MainActivity"
    }

    private val unreadCountReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "REFRESH_UNREAD_COUNT") {
                Log.d(TAG, "Получен запрос на обновление счётчика непрочитанных")
                // Обновляем ViewModel
                val messagesViewModel = ViewModelProvider(this@MainActivity)[MessagesViewModel::class.java]
                messagesViewModel.refreshUnreadCount()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Установка локали и темы
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        val locale = Locale("ru")
        Locale.setDefault(locale)
        val config = android.content.res.Configuration()
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)

        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate START")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)



        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        sessionManager = SessionManager(this)
        tokenManager = TokenManager.getInstance(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
            }
        }

        val filter = IntentFilter("REFRESH_UNREAD_COUNT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(unreadCountReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(unreadCountReceiver, filter)
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        bottomNavigation = binding.bottomNavigation

        // Скрываем bottomNavigation до настройки графа
        bottomNavigation.visibility = View.GONE

        // Вся логика, зависящая от авторизации – внутри корутины
        lifecycleScope.launch {
            Log.d("MainActivity", "Inside launch block")
            val isLoggedIn = sessionManager.isLoggedIn()

            // Настройка графа навигации
            val inflater: NavInflater = navController.navInflater
            val graph: NavGraph = inflater.inflate(R.navigation.nav_graph)
            if (isLoggedIn) {
                graph.setStartDestination(R.id.navigation_main)
            } else {
                graph.setStartDestination(R.id.loginFragment)
            }
            navController.graph = graph

            if (!isLoggedIn) {
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
            }

            // Определяем видимость bottomNavigation
            val currentDest = navController.currentDestination
            val noMenuFragments = setOf(
                R.id.loginFragment, R.id.registerFragment, R.id.forgotPasswordFragment
            )
            val showMenu = isLoggedIn && currentDest?.id !in noMenuFragments
            showBottomNavigation(showMenu)
            supportActionBar?.setDisplayHomeAsUpEnabled(currentDest?.id != R.id.loginFragment)

            // Слушатель смены фрагмента (управление меню и стрелкой)
            navController.addOnDestinationChangedListener { _, destination, _ ->
                lifecycleScope.launch {
                    val isLoggedInNow = sessionManager.isLoggedIn()
                    val noMenu = setOf(R.id.loginFragment, R.id.registerFragment, R.id.forgotPasswordFragment)
                    val show = isLoggedInNow && destination.id !in noMenu
                    showBottomNavigation(show)
                    supportActionBar?.setDisplayHomeAsUpEnabled(destination.id != R.id.loginFragment)
                }
            }

            // Настройка навигации с bottomNavigation и ActionBar
            //NavigationUI.setupWithNavController(bottomNavigation, navController)
            NavigationUI.setupActionBarWithNavController(this@MainActivity, navController)
            bottomNavigation.setOnItemSelectedListener { item ->
                navigateToDestination(item.itemId)
                true
            }

            navController.addOnDestinationChangedListener { _, destination, _ ->
                val menuItemId = when (destination.id) {
                    R.id.navigation_main,
                    R.id.navigation_create,
                    R.id.navigation_my_ads,
                    R.id.navigation_responses,
                    R.id.navigation_profile -> destination.id
                    else -> null
                }
                menuItemId?.let {
                    if (bottomNavigation.selectedItemId != it) {
                        bottomNavigation.selectedItemId = it
                    }
                }
            }

            bottomNavigation.isClickable = true
            bottomNavigation.isFocusable = true
            bottomNavigation.isEnabled = true
            bottomNavigation.bringToFront()

            TokenRefreshScheduler.scheduleNextRefresh(this@MainActivity)

            // AuthListener для обработки 401
            val authListener = object : ApiClient.AuthListener {
                override fun onUnauthorized() {
                    Log.e(TAG, "!!! onUnauthorized called !!!")
                    runOnUiThread {
                        lifecycleScope.launch {
                            sessionManager.logout()
                        }
                        tokenManager.clear()
                        navigateToLoginWithClearStack()
                        Toast.makeText(this@MainActivity, R.string.session_reset, Toast.LENGTH_LONG).show()
                    }
                }
            }
            ApiClient.addAuthListener(authListener)
            this@MainActivity.authListener = authListener

            NotificationHelper.checkAndRequestNotificationPermission(this@MainActivity, NOTIFICATION_PERMISSION_REQUEST_CODE)

            ApiClient.checkServerAvailability(object : ApiClient.UrlCheckCallback {
                override fun onUrlReady(baseUrl: String) {
                    Log.d(TAG, "Using URL: $baseUrl")
                    runOnUiThread {
                        if (savedInstanceState == null) {
                            navigateToDefaultScreen()
                        }
                        if (isLoggedIn) {
                            WebSocketForegroundService.start(this@MainActivity)
                        }
                    }
                    lifecycleScope.launch {
                        val accepted = PrivacyHelper.showPrivacyDialogIfNeeded(
                            context = this@MainActivity,
                            lifecycleScope = this@MainActivity.lifecycleScope
                        )
                        if (!accepted) {
                            // Если пользователь не согласился (теоретически не может, т.к. только "Принимаю"),
                            // можно закрыть приложение
                            finishAffinity()
                        }
                    }
                }
                override fun onError(error: String) {
                    Log.e(TAG, "Error checking URL: $error")
                }
            })

            handlePushIntent(intent)
        }

        // Эти наблюдения не требуют авторизации и могут быть вне корутины
        lifecycleScope.launch {
            mainViewModel.error.collect { error ->
                error?.let {
                    // Показываем ошибку через ToastHelper или просто Toast
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                    mainViewModel.clearError()
                }
            }
        }

        lifecycleScope.launch {
            mainViewModel.shouldNavigateToLogin.collect { shouldNavigate ->
                if (shouldNavigate) {
                    navigateToLoginWithClearStack()
                    mainViewModel.resetNavigationFlag()
                }
            }
        }
    }

    private fun navigateToLoginWithClearStack() {
        val navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
        navGraph.setStartDestination(R.id.loginFragment)
        navController.graph = navGraph
        navController.navigate(R.id.loginFragment)
        showBottomNavigation(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        refreshMenu()
    }

    private fun showBottomNavigation(show: Boolean) {
        bottomNavigation.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePushIntent(intent)
    }

    private fun handlePushIntent(intent: Intent) {
        // закомментировано, при необходимости раскомментировать
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        optionsMenu = menu
        updateMenuVisibility()

        val notificationItem = menu.findItem(R.id.action_notifications)
        val actionView = notificationItem?.actionView
        if (actionView != null) {
            badgeView = actionView.findViewById(R.id.badge)
            val messagesViewModel = ViewModelProvider(this)[MessagesViewModel::class.java]
            lifecycleScope.launch {
                messagesViewModel.unreadCount.collect { count ->
                    badgeView.text = if (count > 0) count.toString() else ""
                    badgeView.visibility = if (count > 0) View.VISIBLE else View.GONE
                }
            }
            actionView.setOnClickListener {
                Navigation.findNavController(this, R.id.nav_host_fragment)
                    .navigate(R.id.messagesFragment)
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_admin -> {
                lifecycleScope.launch {
                    if (sessionManager.isAdmin()) {
                        val navController = Navigation.findNavController(this@MainActivity, R.id.nav_host_fragment)
                        val navOptions = NavOptions.Builder()
                            .setPopUpTo(R.id.navigation_main, false) // false – оставляем главную в стеке
                            .build()
                        navController.navigate(R.id.adminFragment, null, navOptions)
                    } else {
                        Toast.makeText(this@MainActivity, R.string.access_denied, Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }
            R.id.action_notifications -> {
                val navController = Navigation.findNavController(this, R.id.nav_host_fragment)
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.navigation_main, false)
                    .build()
                navController.navigate(R.id.messagesFragment, null, navOptions)
                true
            }
            R.id.action_feedback -> {
                val navController = Navigation.findNavController(this, R.id.nav_host_fragment)
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.navigation_main, false)
                    .build()
                navController.navigate(R.id.feedbackFragment, null, navOptions)
                true
            }
            R.id.action_test_push -> {
                sendTestPush()
                true
            }
            R.id.action_register_tokens -> {
                registerPushTokens()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun registerPushTokens() {
        lifecycleScope.launch {
            // Проверяем, залогинен ли пользователь
            val isLoggedIn = sessionManager.isLoggedIn()
            if (!isLoggedIn) {
                Toast.makeText(this@MainActivity, "Сначала войдите в систему", Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                val tokenRegistrationService = TokenRegistrationService(this@MainActivity)
                tokenRegistrationService.registerAllTokens()
                Toast.makeText(
                    this@MainActivity,
                    "✅ Регистрация токенов запущена. Проверьте логи.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "❌ Ошибка: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                Log.e("MainActivity", "Ошибка регистрации токенов", e)
            }
        }
    }

    private fun sendTestPush() {
        lifecycleScope.launch {
            try {
                val repository = com.katok.pro.repository.NotificationRepository()
                // Задаём заголовок и тело уведомления
                val title = "🔔 Тестовое push-уведомление"
                val body = "Если вы это видите – push работает!"
                val result = repository.sendTestPush(title, body)
                if (result is com.katok.pro.model.NetworkResult.Success) {
                    Toast.makeText(this@MainActivity, "✅ Тестовый push отправлен", Toast.LENGTH_SHORT).show()
                } else if (result is com.katok.pro.model.NetworkResult.Error) {
                    Toast.makeText(this@MainActivity, "❌ Ошибка: ${result.message}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "ℹ️ Статус: ${result::class.simpleName}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "❌ Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Notification permission granted")
            } else {
                Log.d(TAG, "Notification permission denied")
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        if (!navController.navigateUp()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
        if (navController.currentDestination?.id == R.id.navigation_main) {
            // Проверяем, есть ли в стеке другие фрагменты (т.е. не корневой)
            // Если стек пуст (только главный), показываем диалог
            if (navController.graph.startDestinationId == R.id.navigation_main) {
                showExitDialog()
                return
            }
        }
        // Иначе стандартное поведение – переход назад
        super.onBackPressed()

    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Выход")
            .setMessage("Вы уверены, что хотите выйти из приложения?")
            .setPositiveButton("Да") { _, _ ->
                finishAffinity()  // закрывает все активности
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(unreadCountReceiver)
        } catch (e: Exception) {
            // игнорируем, если ресивер не был зарегистрирован
        }
        authListener?.let { ApiClient.removeAuthListener(it) }
        super.onDestroy()
    }

    private fun updateMenuVisibility() {
        if (optionsMenu == null) return
        val adminItem = optionsMenu?.findItem(R.id.action_admin)
        val feedbackItem = optionsMenu?.findItem(R.id.action_feedback)

        lifecycleScope.launch {
            val isAdmin = sessionManager.isAdmin()
            adminItem?.isVisible = isAdmin
            feedbackItem?.isVisible = !isAdmin
        }
    }

    override fun onResume() {
        super.onResume()
        invalidateOptionsMenu() // пересоздаёт меню при возврате на активность
    }

    private fun navigateToDestination(destinationId: Int) {
        // Если пытаемся перейти к созданию объявления, проверяем профиль
        if (destinationId == R.id.navigation_create) {
            lifecycleScope.launch {
                val userRepo = UserRepository(this@MainActivity)
                val navController = Navigation.findNavController(this@MainActivity, R.id.nav_host_fragment)
                val profile = ProfileHelper.getValidProfileOrShowDialog(
                    context = this@MainActivity,
                    userRepository = userRepo,
                    navController = navController
                )
                if (profile == null) {
                    return@launch // профиль не заполнен – диалог уже показан, прерываем навигацию
                }
                // Если профиль заполнен – продолжаем навигацию
                performNavigateToDestination(destinationId)
            }
            return
        }
        // Для остальных пунктов меню – обычная навигация
        performNavigateToDestination(destinationId)
    }

    // Вынесите старую логику в отдельный метод
    private fun performNavigateToDestination(destinationId: Int) {
        val navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        if (navController.currentDestination?.id == destinationId) {
            return
        }
        val popped = navController.popBackStack(destinationId, false)
        if (!popped) {
            navController.navigate(destinationId)
        }
        bottomNavigation.selectedItemId = destinationId
    }

    private fun navigateToDefaultScreen() {
        lifecycleScope.launch {
            val isLoggedIn = sessionManager.isLoggedIn()
            runOnUiThread {
                if (!isLoggedIn) {
                    navigateToLoginWithClearStack()
                } else {
                    if (!isTokenChecked) {
                        mainViewModel.checkTokenAndProceed(
                            onTokenValid = {
                                runOnUiThread {
                                    val navController = Navigation.findNavController(this@MainActivity, R.id.nav_host_fragment)
                                    if (navController.currentDestination?.id != R.id.navigation_main) {
                                        navController.navigate(R.id.navigation_main)
                                    }
                                }
                            },
                            onTokenInvalid = {
                                runOnUiThread {
                                    // Показываем понятное сообщение и переходим на логин
                                    Toast.makeText(this@MainActivity, R.string.error_unauthorized, Toast.LENGTH_LONG).show()
                                    navigateToLoginWithClearStack()
                                }
                            }
                        )
                        isTokenChecked = true
                    }
                    refreshMenu()
                }
            }
        }
    }

    fun refreshMenu() {
        // Принудительно пересоздаём меню
        invalidateOptionsMenu()
        // Обновляем видимость пунктов
        updateMenuVisibility()
    }

    fun resetTokenCheck() {
        isTokenChecked = false
    }

}