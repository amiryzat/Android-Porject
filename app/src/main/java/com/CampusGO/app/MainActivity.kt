package com.CampusGO.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.CampusGO.app.databinding.ActivityMainBinding
import com.CampusGO.app.fragment.ChatListFragment
import com.CampusGO.app.fragment.FeedFragment
import com.CampusGO.app.fragment.ProfileFragment
import com.CampusGO.app.fragment.TasksFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            loadFragment(FeedFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_feed -> FeedFragment()
                R.id.nav_tasks -> TasksFragment()
                R.id.nav_chat -> ChatListFragment()
                R.id.nav_profile -> ProfileFragment()
                else -> FeedFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tabId = intent.getIntExtra("openTab", -1)
        if (tabId != -1) {
            binding.bottomNav.selectedItemId = tabId
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commitAllowingStateLoss()
    }
}
