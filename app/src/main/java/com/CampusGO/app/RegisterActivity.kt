package com.CampusGO.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.CampusGO.app.databinding.ActivityRegisterBinding
import com.CampusGO.app.model.User
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        InsetsHelper.applyTopInsetPadding(binding.scrollRoot)

        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()
            if (!validate(name, email, password, confirmPassword)) return@setOnClickListener

            setLoading(true)
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val firebaseUser = result.user ?: return@addOnSuccessListener
                    val uid = firebaseUser.uid

                    val profileUpdate = UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                    firebaseUser.updateProfile(profileUpdate)

                    val user = User(
                        uid = uid,
                        fullName = name,
                        email = email,
                        createdAt = System.currentTimeMillis()
                    )
                    val initialStats = mapOf(
                        "completedTasks" to 0,
                        "postedTasks"    to 0,
                        "acceptedTasks"  to 0,
                        "rating"         to 5.0,
                        "totalReviews"   to 0
                    )
                    database.reference.child("users").child(uid).setValue(user)
                        .addOnSuccessListener {
                            // Create userStats alongside the user record
                            database.reference.child("userStats").child(uid).setValue(initialStats)
                            startActivity(
                                Intent(this, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                            )
                        }
                        .addOnFailureListener { e ->
                            setLoading(false)
                            showError(e.message ?: "Failed to save profile. Please try again.")
                        }
                }
                .addOnFailureListener { e ->
                    setLoading(false)
                    showError(e.message ?: "Registration failed. Please try again.")
                }
        }

        binding.tvLogin.setOnClickListener { finish() }
    }

    private fun validate(name: String, email: String, password: String, confirmPassword: String): Boolean {
        var valid = true
        if (name.isEmpty()) {
            binding.nameInputLayout.error = "Full name is required"
            valid = false
        } else {
            binding.nameInputLayout.error = null
        }
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
        if (confirmPassword != password) {
            binding.confirmPasswordInputLayout.error = "Passwords do not match"
            valid = false
        } else {
            binding.confirmPasswordInputLayout.error = null
        }
        return valid
    }

    private fun setLoading(loading: Boolean) {
        binding.btnRegister.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
