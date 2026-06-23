package com.CampusGO.app.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.CampusGO.app.LoginActivity
import com.CampusGO.app.WalletCashOutActivity
import com.CampusGO.app.WalletTopUpActivity
import com.CampusGO.app.adapter.WalletTransactionAdapter
import com.CampusGO.app.databinding.FragmentProfileBinding
import com.CampusGO.app.model.User
import com.CampusGO.app.model.Wallet
import com.CampusGO.app.model.WalletTransaction
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

    companion object {
        private const val TAG = "ProfileFragment"
    }

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

        db.child("users").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return
                    val user = snapshot.getValue(User::class.java)
                    if (user != null && user.fullName.isNotEmpty()) {
                        binding.tvAvatarLarge.text =
                            user.fullName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                        binding.tvFullName.text = user.fullName
                        binding.tvEmail.text = user.email
                    } else {
                        Log.w(TAG, "No /users/$uid record — creating from Auth data")
                        val newUser = User(
                            uid = uid,
                            fullName = authName,
                            email = authUser.email ?: "",
                            createdAt = System.currentTimeMillis()
                        )
                        db.child("users").child(uid).setValue(newUser)
                        ensureUserStats(uid)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "users read failed: ${error.message}")
                }
            })

        db.child("userStats").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
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

        binding.btnTopUp.setOnClickListener {
            startActivity(Intent(requireContext(), WalletTopUpActivity::class.java))
        }

        binding.btnCashOut.setOnClickListener {
            startActivity(Intent(requireContext(), WalletCashOutActivity::class.java))
        }

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
            transactionListener?.let {
                db.child("walletTransactions").child(uid).removeEventListener(it)
            }
        }
        _binding = null
    }
}
