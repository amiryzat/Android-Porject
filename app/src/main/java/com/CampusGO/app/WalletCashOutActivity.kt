package com.CampusGO.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.CampusGO.app.databinding.ActivityWalletCashOutBinding
import com.CampusGO.app.model.Wallet
import com.CampusGO.app.model.WalletTransaction
import com.CampusGO.app.model.WalletTransactionStatus
import com.CampusGO.app.model.WalletTransactionType
import com.CampusGO.app.util.handleKeyboardInsets
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class WalletCashOutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalletCashOutBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    private var currentWallet: Wallet? = null
    private var selectedBank = ""
    private var walletListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletCashOutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handleKeyboardInsets(binding.scrollView)
        setupBankPicker()
        loadWalletBalance()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSubmit.setOnClickListener { onSubmitTapped() }
    }

    private fun loadWalletBalance() {
        val uid = auth.currentUser?.uid ?: return
        walletListener = db.child("wallets").child(uid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val wallet = snapshot.getValue(Wallet::class.java)
                    currentWallet = wallet
                    val balance = wallet?.balance ?: 0.0
                    binding.tvAvailableBalance.text = "RM ${String.format("%.2f", balance)}"
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun setupBankPicker() {
        val openPicker = {
            val picker = BankPickerBottomSheet()
            picker.onBankSelected = { bank ->
                selectedBank = bank
                binding.etBankName.setText(bank)
                binding.layoutBankName.error = null
            }
            picker.show(supportFragmentManager, "bank_picker")
        }
        binding.etBankName.setOnClickListener { openPicker() }
    }

    private fun onSubmitTapped() {
        val wallet = currentWallet ?: run {
            Toast.makeText(this, "Wallet data is loading, please wait.", Toast.LENGTH_SHORT).show()
            return
        }

        val amountStr = binding.etAmount.text.toString().trim()
        val amount = amountStr.toDoubleOrNull()
        val accountNumber = binding.etAccountNumber.text.toString().trim()
        val accountHolder = binding.etAccountHolder.text.toString().trim()

        binding.layoutAmount.error = null
        binding.layoutBankName.error = null
        binding.layoutAccountNumber.error = null
        binding.layoutAccountHolder.error = null

        var valid = true

        when {
            amount == null || amount <= 0 -> {
                binding.layoutAmount.error = "Enter a valid amount greater than RM 0"
                valid = false
            }
            amount > wallet.balance -> {
                binding.layoutAmount.error =
                    "Amount exceeds available balance (RM ${String.format("%.2f", wallet.balance)})"
                valid = false
            }
        }

        if (selectedBank.isEmpty()) {
            binding.layoutBankName.error = "Please select a bank"
            valid = false
        }

        if (accountNumber.isEmpty()) {
            binding.layoutAccountNumber.error = "Account number is required"
            valid = false
        }

        if (accountHolder.isEmpty()) {
            binding.layoutAccountHolder.error = "Account holder name is required"
            valid = false
        }

        if (!valid || amount == null) return

        performCashOut(amount, selectedBank, accountNumber, accountHolder)
    }

    private fun performCashOut(
        amount: Double,
        bankName: String,
        accountNumber: String,
        accountHolder: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        val wallet = currentWallet ?: return

        binding.btnSubmit.isEnabled = false

        val newBalance = wallet.balance - amount
        val newTotalSpent = wallet.totalSpent + amount

        val txRef = db.child("walletTransactions").child(uid).push()
        val txId = txRef.key ?: run {
            binding.btnSubmit.isEnabled = true
            return
        }

        val tx = WalletTransaction(
            id = txId,
            type = WalletTransactionType.CASH_OUT,
            amount = amount,
            status = WalletTransactionStatus.COMPLETED,
            description = "Cash Out to $bankName",
            createdAt = System.currentTimeMillis()
        )

        txRef.setValue(tx)
            .addOnSuccessListener {
                db.child("wallets").child(uid).updateChildren(
                    mapOf(
                        "balance" to newBalance,
                        "totalSpent" to newTotalSpent,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).addOnSuccessListener {
                    startActivity(Intent(this, WalletCashOutSuccessActivity::class.java).apply {
                        putExtra("amount", amount)
                        putExtra("bankName", bankName)
                        putExtra("accountNumber", accountNumber)
                    })
                    finish()
                }.addOnFailureListener { e ->
                    binding.btnSubmit.isEnabled = true
                    Toast.makeText(this, "Failed to update wallet: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                binding.btnSubmit.isEnabled = true
                Toast.makeText(this, "Cash out failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        val uid = auth.currentUser?.uid ?: return
        walletListener?.let { db.child("wallets").child(uid).removeEventListener(it) }
    }
}
