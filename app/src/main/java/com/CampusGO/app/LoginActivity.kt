package com.CampusGO.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.CampusGO.app.databinding.ActivityLoginBinding
import com.CampusGO.app.model.User
import com.CampusGO.app.model.Wallet
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth     = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            if (!validate(email, password)) return@setOnClickListener
            setLoading(true)
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val firebaseUser = result.user
                    if (firebaseUser != null) {
                        // Ensure the user has a /users and /userStats record in the database.
                        // This handles accounts that were created before the DB was set up,
                        // or accounts created directly in the Firebase console.
                        syncUserToDatabase(
                            uid         = firebaseUser.uid,
                            displayName = firebaseUser.displayName,
                            email       = firebaseUser.email
                        )
                    }
                    startActivity(
                        Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                }
                .addOnFailureListener { e ->
                    setLoading(false)
                    showError(e.message ?: "Sign in failed. Please try again.")
                }
        }

        binding.tvForgotPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty()) {
                binding.emailInputLayout.error = "Enter your email address first"
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Snackbar.make(binding.root, "Password reset link sent to $email", Snackbar.LENGTH_LONG).show()
                }
                .addOnFailureListener { e ->
                    showError(e.message ?: "Could not send reset email")
                }
        }

        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun validate(email: String, password: String): Boolean {
        var valid = true
        if (email.isEmpty()) {
            binding.emailInputLayout.error = "Email is required"
            valid = false
        } else {
            binding.emailInputLayout.error = null
        }
        if (password.isEmpty()) {
            binding.passwordInputLayout.error = "Password is required"
            valid = false
        } else if (password.length < 6) {
            binding.passwordInputLayout.error = "Password must be at least 6 characters"
            valid = false
        } else {
            binding.passwordInputLayout.error = null
        }
        return valid
    }

    private fun syncUserToDatabase(uid: String, displayName: String?, email: String?) {
        val dbRef = database.reference
        dbRef.child("users").child(uid).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists() || snapshot.child("fullName").getValue(String::class.java).isNullOrEmpty()) {
                val name = displayName?.takeIf { it.isNotEmpty() }
                    ?: email?.substringBefore("@")
                    ?: "User"
                val user = User(
                    uid       = uid,
                    fullName  = name,
                    email     = email ?: "",
                    createdAt = System.currentTimeMillis()
                )
                dbRef.child("users").child(uid).setValue(user)
            }
            // Ensure userStats exists
            dbRef.child("userStats").child(uid).get().addOnSuccessListener { statsSnap ->
                if (!statsSnap.exists()) {
                    dbRef.child("userStats").child(uid).setValue(
                        mapOf(
                            "completedTasks" to 0,
                            "postedTasks"    to 0,
                            "acceptedTasks"  to 0,
                            "rating"         to 5.0,
                            "totalReviews"   to 0
                        )
                    )
                }
            }
            // Ensure wallet exists
            dbRef.child("wallets").child(uid).get().addOnSuccessListener { walletSnap ->
                if (!walletSnap.exists()) {
                    val wallet = Wallet(
                        userId = uid,
                        balance = 0.0,
                        totalEarned = 0.0,
                        totalSpent = 0.0,
                        updatedAt = System.currentTimeMillis()
                    )
                    dbRef.child("wallets").child(uid).setValue(wallet)
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
