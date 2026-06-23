package com.CampusGO.app.fragment

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.CampusGO.app.LoginActivity
import com.CampusGO.app.R
import com.CampusGO.app.adapter.WalletTransactionAdapter
import com.CampusGO.app.databinding.FragmentProfileBinding
import com.CampusGO.app.model.User
import com.CampusGO.app.model.Wallet
import com.CampusGO.app.model.WalletTransaction
import com.CampusGO.app.model.WalletTransactionStatus
import com.CampusGO.app.model.WalletTransactionType
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db by lazy { FirebaseDatabase.getInstance().reference }

    private lateinit var transactionAdapter: WalletTransactionAdapter

    private var walletListener: ValueEventListener? = null
    private var transactionListener: ValueEventListener? = null
    private var currentWallet: Wallet? = null

    companion object {
        private const val TAG = "ProfileFragment"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val authUser = auth.currentUser ?: return
        val uid = authUser.uid

        val authName = authUser.displayName
            ?.takeIf { it.isNotEmpty() }
            ?: authUser.email?.substringBefore("@")
            ?: "User"

        binding.tvAvatarLarge.text = authName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        binding.tvFullName.text = authName
        binding.tvEmail.text = authUser.email ?: ""
        binding.tvRating.text = "5.0 ★"
        binding.tvCompleted.text = "0"
        binding.tvPosted.text = "0"

        db.child("users").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val user = snapshot.getValue(User::class.java)
                if (user != null && user.fullName.isNotEmpty()) {
                    binding.tvAvatarLarge.text = user.fullName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    binding.tvFullName.text = user.fullName
                    binding.tvEmail.text = user.email
                } else {
                    Log.w(TAG, "No /users/$uid record — creating from Auth data")
                    val newUser = User(uid = uid, fullName = authName, email = authUser.email ?: "", createdAt = System.currentTimeMillis())
                    db.child("users").child(uid).setValue(newUser)
                    ensureUserStats(uid)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "users read failed: ${error.message}")
            }
        })

        db.child("userStats").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val rating = snapshot.child("rating").getValue(Double::class.java) ?: 5.0
                val completed = snapshot.child("completedTasks").getValue(Int::class.java) ?: 0
                val posted = snapshot.child("postedTasks").getValue(Int::class.java) ?: 0
                binding.tvRating.text = String.format("%.1f ★", rating)
                binding.tvCompleted.text = completed.toString()
                binding.tvPosted.text = posted.toString()
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "userStats read failed: ${error.message}")
            }
        })

        setupTransactionRecyclerView()
        ensureWalletExists(uid)
        listenWallet(uid)
        listenTransactions(uid)

        binding.btnTopUp.setOnClickListener { showTopUpDialog() }
        binding.btnCashOut.setOnClickListener { showCashOutDialog() }

        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    private fun setupTransactionRecyclerView() {
        transactionAdapter = WalletTransactionAdapter()
        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = transactionAdapter
        binding.rvTransactions.isNestedScrollingEnabled = false
    }

    private fun ensureWalletExists(uid: String) {
        db.child("wallets").child(uid).get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    val wallet = Wallet(
                        userId = uid,
                        balance = 0.0,
                        totalEarned = 0.0,
                        totalSpent = 0.0,
                        updatedAt = System.currentTimeMillis()
                    )
                    db.child("wallets").child(uid).setValue(wallet)
                        .addOnSuccessListener { Log.d(TAG, "Wallet created for $uid") }
                        .addOnFailureListener { e -> Log.e(TAG, "Failed to create wallet", e) }
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to check wallet", e) }
    }

    private fun listenWallet(uid: String) {
        walletListener = db.child("wallets").child(uid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return
                    val wallet = snapshot.getValue(Wallet::class.java) ?: return
                    currentWallet = wallet
                    updateWalletUI(wallet)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Wallet listener cancelled: ${error.message}")
                }
            })
    }

    private fun listenTransactions(uid: String) {
        transactionListener = db.child("walletTransactions").child(uid)
            .orderByChild("createdAt")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return
                    val transactions = mutableListOf<WalletTransaction>()
                    for (child in snapshot.children) {
                        child.getValue(WalletTransaction::class.java)?.let { transactions.add(it) }
                    }
                    transactions.sortByDescending { it.createdAt }
                    transactionAdapter.setTransactions(transactions)

                    val isEmpty = transactions.isEmpty()
                    binding.tvEmptyTransactions.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    binding.rvTransactions.visibility = if (isEmpty) View.GONE else View.VISIBLE
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Transaction listener cancelled: ${error.message}")
                }
            })
    }

    private fun updateWalletUI(wallet: Wallet) {
        binding.tvWalletBalance.text = "RM ${String.format("%.2f", wallet.balance)}"
        binding.tvTotalEarned.text = "Total Earned: RM ${String.format("%.2f", wallet.totalEarned)}"
        binding.tvTotalSpent.text = "Total Spent: RM ${String.format("%.2f", wallet.totalSpent)}"
    }

    // ─── Top Up ───────────────────────────────────────────────────────────────

    private fun showTopUpDialog() {
        if (currentWallet == null) {
            Toast.makeText(requireContext(), "Wallet is loading, please wait.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_top_up, null)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.chipGroupAmount)
        val layoutCustom = dialogView.findViewById<TextInputLayout>(R.id.layoutCustomAmount)
        val etCustom = dialogView.findViewById<TextInputEditText>(R.id.etCustomAmount)

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            layoutCustom.visibility = if (checkedIds.contains(R.id.chipCustom)) View.VISIBLE else View.GONE
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Top Up Wallet")
            .setView(dialogView)
            .setPositiveButton("Top Up", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val checkedId = chipGroup.checkedChipId
            val amount: Double = when (checkedId) {
                R.id.chip10 -> 10.0
                R.id.chip20 -> 20.0
                R.id.chip50 -> 50.0
                R.id.chip100 -> 100.0
                R.id.chipCustom -> etCustom.text.toString().toDoubleOrNull() ?: 0.0
                else -> 0.0
            }

            when {
                checkedId == View.NO_ID ->
                    Toast.makeText(requireContext(), "Please select an amount.", Toast.LENGTH_SHORT).show()
                amount <= 0 ->
                    Toast.makeText(requireContext(), "Please enter a valid amount greater than RM 0.", Toast.LENGTH_SHORT).show()
                else -> {
                    dialog.dismiss()
                    performTopUp(amount)
                }
            }
        }
    }

    private fun performTopUp(amount: Double) {
        val uid = auth.currentUser?.uid ?: return
        val wallet = currentWallet ?: return

        val newBalance = wallet.balance + amount
        val newTotalEarned = wallet.totalEarned + amount

        val txRef = db.child("walletTransactions").child(uid).push()
        val txId = txRef.key ?: return

        val tx = WalletTransaction(
            id = txId,
            type = WalletTransactionType.TOP_UP,
            amount = amount,
            status = WalletTransactionStatus.COMPLETED,
            description = "Wallet Top Up",
            createdAt = System.currentTimeMillis()
        )

        txRef.setValue(tx).addOnSuccessListener {
            db.child("wallets").child(uid).updateChildren(
                mapOf(
                    "balance" to newBalance,
                    "totalEarned" to newTotalEarned,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).addOnSuccessListener {
                showSuccessDialog(
                    "Top Up Successful",
                    "RM ${String.format("%.2f", amount)} added to your wallet."
                )
            }.addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to update balance: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Top up failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Cash Out ─────────────────────────────────────────────────────────────

    private fun showCashOutDialog() {
        val wallet = currentWallet
        if (wallet == null) {
            Toast.makeText(requireContext(), "Wallet is loading, please wait.", Toast.LENGTH_SHORT).show()
            return
        }
        if (wallet.balance <= 0) {
            Toast.makeText(requireContext(), "Insufficient balance to cash out.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_cash_out, null)
        val etAmount = dialogView.findViewById<TextInputEditText>(R.id.etAmount)
        val etBankName = dialogView.findViewById<TextInputEditText>(R.id.etBankName)
        val etAccountNumber = dialogView.findViewById<TextInputEditText>(R.id.etAccountNumber)
        val etAccountHolder = dialogView.findViewById<TextInputEditText>(R.id.etAccountHolder)
        val layoutAmount = dialogView.findViewById<TextInputLayout>(R.id.layoutAmount)
        val layoutBankName = dialogView.findViewById<TextInputLayout>(R.id.layoutBankName)
        val layoutAccountNumber = dialogView.findViewById<TextInputLayout>(R.id.layoutAccountNumber)
        val layoutAccountHolder = dialogView.findViewById<TextInputLayout>(R.id.layoutAccountHolder)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Cash Out")
            .setMessage("Available: RM ${String.format("%.2f", wallet.balance)}")
            .setView(dialogView)
            .setPositiveButton("Withdraw", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val amount = etAmount.text.toString().toDoubleOrNull()
            val bankName = etBankName.text.toString().trim()
            val accountNumber = etAccountNumber.text.toString().trim()
            val accountHolder = etAccountHolder.text.toString().trim()

            layoutAmount.error = null
            layoutBankName.error = null
            layoutAccountNumber.error = null
            layoutAccountHolder.error = null

            var valid = true

            if (amount == null || amount <= 0) {
                layoutAmount.error = "Enter a valid amount"
                valid = false
            } else if (amount > wallet.balance) {
                layoutAmount.error = "Amount exceeds available balance (RM ${String.format("%.2f", wallet.balance)})"
                valid = false
            }
            if (bankName.isEmpty()) {
                layoutBankName.error = "Bank name is required"
                valid = false
            }
            if (accountNumber.isEmpty()) {
                layoutAccountNumber.error = "Account number is required"
                valid = false
            }
            if (accountHolder.isEmpty()) {
                layoutAccountHolder.error = "Account holder name is required"
                valid = false
            }

            if (valid && amount != null) {
                dialog.dismiss()
                performCashOut(amount, bankName)
            }
        }
    }

    private fun performCashOut(amount: Double, bankName: String) {
        val uid = auth.currentUser?.uid ?: return
        val wallet = currentWallet ?: return

        val newBalance = wallet.balance - amount
        val newTotalSpent = wallet.totalSpent + amount

        val txRef = db.child("walletTransactions").child(uid).push()
        val txId = txRef.key ?: return

        val tx = WalletTransaction(
            id = txId,
            type = WalletTransactionType.CASH_OUT,
            amount = amount,
            status = WalletTransactionStatus.COMPLETED,
            description = "Cash Out to $bankName",
            createdAt = System.currentTimeMillis()
        )

        txRef.setValue(tx).addOnSuccessListener {
            db.child("wallets").child(uid).updateChildren(
                mapOf(
                    "balance" to newBalance,
                    "totalSpent" to newTotalSpent,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).addOnSuccessListener {
                showSuccessDialog(
                    "Cash Out Successful",
                    "RM ${String.format("%.2f", amount)} transferred to $bankName."
                )
            }.addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to update balance: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Cash out failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun showSuccessDialog(title: String, message: String) {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun ensureUserStats(uid: String) {
        db.child("userStats").child(uid).get().addOnSuccessListener { snap ->
            if (!snap.exists()) {
                db.child("userStats").child(uid).setValue(
                    mapOf(
                        "completedTasks" to 0,
                        "postedTasks" to 0,
                        "acceptedTasks" to 0,
                        "rating" to 5.0,
                        "totalReviews" to 0
                    )
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val uid = auth.currentUser?.uid
        if (uid != null) {
            walletListener?.let { db.child("wallets").child(uid).removeEventListener(it) }
            transactionListener?.let { db.child("walletTransactions").child(uid).removeEventListener(it) }
        }
        _binding = null
    }
}
