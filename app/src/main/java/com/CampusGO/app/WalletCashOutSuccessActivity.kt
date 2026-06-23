package com.CampusGO.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.CampusGO.app.databinding.ActivityWalletCashOutSuccessBinding

class WalletCashOutSuccessActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalletCashOutSuccessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletCashOutSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val amount = intent.getDoubleExtra("amount", 0.0)
        val bankName = intent.getStringExtra("bankName") ?: ""
        val accountNumber = intent.getStringExtra("accountNumber") ?: ""

        binding.tvSuccessAmount.text = "RM ${String.format("%.2f", amount)}"
        binding.tvBankName.text = bankName
        binding.tvAccountMasked.text = maskAccountNumber(accountNumber)

        binding.btnReturn.setOnClickListener { returnToProfile() }
    }

    private fun maskAccountNumber(account: String): String =
        if (account.length > 4) "****${account.takeLast(4)}" else account

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        returnToProfile()
    }

    private fun returnToProfile() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("openTab", R.id.nav_profile)
        })
        finish()
    }
}
