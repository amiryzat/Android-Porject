package com.CampusGO.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.CampusGO.app.databinding.ActivityWalletPaymentProcessingBinding
import com.CampusGO.app.util.applyStatusBarInsets
import com.CampusGO.app.model.WalletTransaction
import com.CampusGO.app.model.WalletTransactionStatus
import com.CampusGO.app.model.WalletTransactionType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class WalletPaymentProcessingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalletPaymentProcessingBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val handler = Handler(Looper.getMainLooper())

    private var amount = 0.0
    private var paymentMethod = ""
    private var bankName = ""
    private var processingComplete = false
    private var returnTo = "PROFILE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalletPaymentProcessingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.llHeader.applyStatusBarInsets()

        amount = intent.getDoubleExtra("amount", 0.0)
        paymentMethod = intent.getStringExtra("paymentMethod") ?: "ONLINE_BANKING"
        bankName = intent.getStringExtra("bankName") ?: ""
        returnTo = intent.getStringExtra("returnTo") ?: "PROFILE"

        binding.btnBack.setOnClickListener {
            if (!processingComplete) finish()
        }

        binding.btnReturnToProfile.setOnClickListener {
            when (returnTo) {
                "CREATE_TASK", "TASK_TRACKING" -> {
                    setResult(RESULT_OK)
                    finish()
                }
                else -> returnToProfile()
            }
        }

        startProcessing()
    }

    private fun startProcessing() {
        // Step 1 is already shown as active in the layout

        handler.postDelayed({
            if (isDestroyed) return@postDelayed
            activateStep(
                doneIcon = binding.tvStep1Icon,
                nextLayout = binding.layoutStep2,
                nextIcon = binding.tvStep2Icon,
                nextLabel = binding.tvStep2Label
            )
        }, 800)

        handler.postDelayed({
            if (isDestroyed) return@postDelayed
            activateStep(
                doneIcon = binding.tvStep2Icon,
                nextLayout = binding.layoutStep3,
                nextIcon = binding.tvStep3Icon,
                nextLabel = binding.tvStep3Label
            )
        }, 1600)

        handler.postDelayed({
            if (isDestroyed) return@postDelayed
            activateStep(
                doneIcon = binding.tvStep3Icon,
                nextLayout = binding.layoutStep4,
                nextIcon = binding.tvStep4Icon,
                nextLabel = binding.tvStep4Label
            )
        }, 2400)

        handler.postDelayed({
            if (isDestroyed) return@postDelayed
            binding.tvStep4Icon.text = "✓"
            binding.tvStep4Icon.setTextColor(getColor(R.color.campusgo_success))
            binding.tvStep4Label.setTextColor(getColor(R.color.campusgo_text_primary))
            commitTopUp()
        }, 3200)
    }

    private fun activateStep(
        doneIcon: android.widget.TextView,
        nextLayout: android.view.View,
        nextIcon: android.widget.TextView,
        nextLabel: android.widget.TextView
    ) {
        doneIcon.text = "✓"
        doneIcon.setTextColor(getColor(R.color.campusgo_success))
        nextLayout.alpha = 1f
        nextIcon.text = "●"
        nextIcon.setTextColor(getColor(R.color.campusgo_primary))
        nextLabel.setTextColor(getColor(R.color.campusgo_text_primary))
    }

    private fun commitTopUp() {
        val uid = auth.currentUser?.uid ?: run {
            showError("Not authenticated. Please sign in again.")
            return
        }

        db.child("wallets").child(uid).get()
            .addOnSuccessListener { snapshot ->
                if (isDestroyed) return@addOnSuccessListener

                val currentBalance = snapshot.child("balance").getValue(Double::class.java) ?: 0.0
                val currentTotalEarned = snapshot.child("totalEarned").getValue(Double::class.java) ?: 0.0

                val description = when {
                    paymentMethod == "DEBIT_CARD" -> "Wallet Top Up via Debit Card"
                    bankName.isNotEmpty() -> "Wallet Top Up via Online Banking ($bankName)"
                    else -> "Wallet Top Up via Online Banking"
                }

                val txRef = db.child("walletTransactions").child(uid).push()
                val txId = txRef.key ?: return@addOnSuccessListener

                val tx = WalletTransaction(
                    id = txId,
                    type = WalletTransactionType.TOP_UP,
                    amount = amount,
                    status = WalletTransactionStatus.COMPLETED,
                    description = description,
                    createdAt = System.currentTimeMillis()
                )

                txRef.setValue(tx)
                    .addOnSuccessListener {
                        db.child("wallets").child(uid).updateChildren(
                            mapOf(
                                "balance" to currentBalance + amount,
                                "totalEarned" to currentTotalEarned + amount,
                                "updatedAt" to System.currentTimeMillis()
                            )
                        ).addOnSuccessListener {
                            processingComplete = true
                            showSuccess()
                        }.addOnFailureListener { e ->
                            showError("Failed to update wallet: ${e.message}")
                        }
                    }
                    .addOnFailureListener { e ->
                        showError("Transaction failed: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                showError("Failed to read wallet: ${e.message}")
            }
    }

    private fun showSuccess() {
        if (isDestroyed) return
        binding.containerProcessing.visibility = View.GONE
        binding.containerSuccess.visibility = View.VISIBLE
        binding.tvSuccessAmount.text = "RM ${String.format("%.2f", amount)} has been added to your wallet."
        binding.btnBack.visibility = View.GONE
    }

    private fun showError(message: String) {
        if (isDestroyed) return
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun returnToProfile() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("openTab", R.id.nav_profile)
        })
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
