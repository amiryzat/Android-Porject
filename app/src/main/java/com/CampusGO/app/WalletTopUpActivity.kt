package com.CampusGO.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.CampusGO.app.databinding.ActivityWalletTopUpBinding
import com.CampusGO.app.model.Wallet
import com.CampusGO.app.util.MalaysianBanks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class WalletTopUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalletTopUpBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    private var currentBalance = 0.0
    private var selectedBank = ""
    private var walletListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletTopUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBankDropdown()
        setupPaymentMethodToggle()
        setupAmountChips()
        loadWalletBalance()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnContinue.setOnClickListener { onContinueTapped() }
    }

    private fun loadWalletBalance() {
        val uid = auth.currentUser?.uid ?: return
        walletListener = db.child("wallets").child(uid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val wallet = snapshot.getValue(Wallet::class.java)
                    currentBalance = wallet?.balance ?: 0.0
                    binding.tvCurrentBalance.text = "RM ${String.format("%.2f", currentBalance)}"
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun setupBankDropdown() {
        val bankAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            MalaysianBanks.banks
        )
        binding.etBankSelection.setAdapter(bankAdapter)
        binding.etBankSelection.threshold = 1

        binding.etBankSelection.setOnItemClickListener { _, _, position, _ ->
            selectedBank = bankAdapter.getItem(position) ?: ""
            binding.layoutBankSelection.error = null
        }

        binding.etBankSelection.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !MalaysianBanks.banks.contains(binding.etBankSelection.text.toString())) {
                selectedBank = ""
            }
        }
    }

    private fun setupPaymentMethodToggle() {
        binding.rgPaymentMethod.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbOnlineBanking -> {
                    binding.sectionOnlineBanking.visibility = View.VISIBLE
                    binding.sectionDebitCard.visibility = View.GONE
                }
                R.id.rbDebitCard -> {
                    binding.sectionOnlineBanking.visibility = View.GONE
                    binding.sectionDebitCard.visibility = View.VISIBLE
                }
                else -> {
                    binding.sectionOnlineBanking.visibility = View.GONE
                    binding.sectionDebitCard.visibility = View.GONE
                }
            }
        }
    }

    private fun setupAmountChips() {
        binding.chipGroupAmount.setOnCheckedStateChangeListener { _, checkedIds ->
            binding.layoutCustomAmount.visibility =
                if (checkedIds.contains(R.id.chipCustom)) View.VISIBLE else View.GONE
            binding.layoutCustomAmount.error = null
        }
    }

    private fun onContinueTapped() {
        val amount = resolveAmount() ?: return

        val methodId = binding.rgPaymentMethod.checkedRadioButtonId
        if (methodId == -1) {
            Toast.makeText(this, "Please select a payment method.", Toast.LENGTH_SHORT).show()
            return
        }

        when (methodId) {
            R.id.rbOnlineBanking -> {
                val typed = binding.etBankSelection.text.toString()
                if (selectedBank.isEmpty() || !MalaysianBanks.banks.contains(typed)) {
                    binding.layoutBankSelection.error = "Please select a bank from the list"
                    return
                }
                binding.layoutBankSelection.error = null
                launchProcessing(amount, "ONLINE_BANKING", selectedBank)
            }
            R.id.rbDebitCard -> {
                if (!validateCardFields()) return
                launchProcessing(amount, "DEBIT_CARD", "")
            }
        }
    }

    private fun resolveAmount(): Double? {
        return when (binding.chipGroupAmount.checkedChipId) {
            R.id.chip10 -> 10.0
            R.id.chip20 -> 20.0
            R.id.chip50 -> 50.0
            R.id.chip100 -> 100.0
            R.id.chipCustom -> {
                val custom = binding.etCustomAmount.text.toString().toDoubleOrNull()
                if (custom == null || custom <= 0) {
                    binding.layoutCustomAmount.error = "Enter a valid amount greater than RM 0"
                    null
                } else {
                    binding.layoutCustomAmount.error = null
                    custom
                }
            }
            View.NO_ID -> {
                Toast.makeText(this, "Please select an amount to top up.", Toast.LENGTH_SHORT).show()
                null
            }
            else -> null
        }
    }

    private fun validateCardFields(): Boolean {
        var valid = true
        if (binding.etCardNumber.text.isNullOrBlank()) {
            binding.layoutCardNumber.error = "Required"
            valid = false
        } else binding.layoutCardNumber.error = null
        if (binding.etCardHolder.text.isNullOrBlank()) {
            binding.layoutCardHolder.error = "Required"
            valid = false
        } else binding.layoutCardHolder.error = null
        if (binding.etExpiry.text.isNullOrBlank()) {
            binding.layoutExpiry.error = "Required"
            valid = false
        } else binding.layoutExpiry.error = null
        if (binding.etCvv.text.isNullOrBlank()) {
            binding.layoutCvv.error = "Required"
            valid = false
        } else binding.layoutCvv.error = null
        return valid
    }

    private fun launchProcessing(amount: Double, method: String, bankName: String) {
        startActivity(Intent(this, WalletPaymentProcessingActivity::class.java).apply {
            putExtra("amount", amount)
            putExtra("paymentMethod", method)
            putExtra("bankName", bankName)
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        val uid = auth.currentUser?.uid ?: return
        walletListener?.let { db.child("wallets").child(uid).removeEventListener(it) }
    }
}
