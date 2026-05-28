package com.CampusGO.app.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.CampusGO.app.LoginActivity
import com.CampusGO.app.R
import com.CampusGO.app.model.User
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ProfileFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db by lazy { FirebaseDatabase.getInstance().reference }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvAvatarLarge = view.findViewById<TextView>(R.id.tvAvatarLarge)
        val tvFullName   = view.findViewById<TextView>(R.id.tvFullName)
        val tvEmail      = view.findViewById<TextView>(R.id.tvEmail)
        val tvRating     = view.findViewById<TextView>(R.id.tvRating)
        val tvCompleted  = view.findViewById<TextView>(R.id.tvCompleted)
        val tvPosted     = view.findViewById<TextView>(R.id.tvPosted)
        val btnSignOut   = view.findViewById<MaterialButton>(R.id.btnSignOut)

        val authUser = auth.currentUser ?: return
        val uid = authUser.uid

        // Immediately populate from Firebase Auth so the profile is NEVER blank,
        // even before the database responds or when rules deny reads.
        val authName = authUser.displayName
            ?.takeIf { it.isNotEmpty() }
            ?: authUser.email?.substringBefore("@")
            ?: "User"
        tvAvatarLarge.text = authName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        tvFullName.text    = authName
        tvEmail.text       = authUser.email ?: ""
        tvRating.text      = "5.0 ★"
        tvCompleted.text   = "0"
        tvPosted.text      = "0"

        // Load richer data from database and overwrite the defaults above
        db.child("users").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val user = snapshot.getValue(User::class.java)
                if (user != null && user.fullName.isNotEmpty()) {
                    tvAvatarLarge.text = user.fullName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    tvFullName.text    = user.fullName
                    tvEmail.text       = user.email
                } else {
                    // Record missing — auto-create it so the app stays in sync
                    Log.w("ProfileFragment", "No /users/$uid record — creating from Auth data")
                    val newUser = User(
                        uid       = uid,
                        fullName  = authName,
                        email     = authUser.email ?: "",
                        createdAt = System.currentTimeMillis()
                    )
                    db.child("users").child(uid).setValue(newUser)
                    ensureUserStats(uid)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileFragment", "users read failed: code=${error.code} msg=${error.message}")
            }
        })

        db.child("userStats").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val rating    = snapshot.child("rating").getValue(Double::class.java) ?: 5.0
                val completed = snapshot.child("completedTasks").getValue(Int::class.java) ?: 0
                val posted    = snapshot.child("postedTasks").getValue(Int::class.java) ?: 0
                tvRating.text    = String.format("%.1f ★", rating)
                tvCompleted.text = completed.toString()
                tvPosted.text    = posted.toString()
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileFragment", "userStats read failed: code=${error.code} msg=${error.message}")
            }
        })

        btnSignOut.setOnClickListener {
            auth.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    private fun ensureUserStats(uid: String) {
        db.child("userStats").child(uid).get().addOnSuccessListener { snap ->
            if (!snap.exists()) {
                db.child("userStats").child(uid).setValue(
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
    }
}
