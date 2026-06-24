package com.CampusGO.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.CampusGO.app.databinding.ActivityManageProfileBinding
import com.CampusGO.app.util.applyStatusBarInsets
import com.CampusGO.app.model.User
import com.CampusGO.app.utils.AvatarHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.ByteArrayOutputStream

class ManageProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageProfileBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    private var currentUserRecord: User? = null
    private var base64ImageString: String? = null

    companion object {
        private const val TAG = "ManageProfileActivity"
    }

    // Launchers for Image selection
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            handleImageUri(uri)
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            handleBitmap(bitmap)
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePictureLauncher.launch(null)
        } else {
            Toast.makeText(this, "Camera permission required to take pictures", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.applyStatusBarInsets()

        // Set up toolbar
        binding.btnBack.setOnClickListener { finish() }

        // Load profile data
        loadProfileData()

        // Avatar Click Listener
        binding.flAvatarContainer.setOnClickListener {
            showImageSourceOptions()
        }

        // Save Button Click
        binding.btnSave.setOnClickListener {
            saveChanges()
        }
        
        // Optional: If started to focus password section directly
        val focusPassword = intent.getBooleanExtra("focusPassword", false)
        if (focusPassword) {
            binding.etPassword.requestFocus()
        }
    }

    private fun loadProfileData() {
        val uid = auth.currentUser?.uid ?: return
        setLoading(true)

        db.child("users").child(uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    setLoading(false)
                    val user = snapshot.getValue(User::class.java)
                    if (user != null) {
                        currentUserRecord = user
                        binding.etName.setText(user.fullName)
                        binding.etEmail.setText(user.email)
                        
                        // Load Avatar
                        AvatarHelper.setAvatar(
                            imageView = binding.ivProfilePicture,
                            name = user.fullName,
                            base64Str = user.profilePicture,
                            fallbackTextView = binding.tvAvatarLarge
                        )
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    setLoading(false)
                    Log.e(TAG, "Failed to load user profile: ${error.message}")
                    Toast.makeText(this@ManageProfileActivity, "Failed to load profile data", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun showImageSourceOptions() {
        val options = arrayOf("Choose from Gallery", "Take Photo", "Cancel")
        AlertDialog.Builder(this)
            .setTitle("Profile Picture")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> pickImageLauncher.launch("image/*")
                    1 -> checkCameraPermissionAndTakePicture()
                    else -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndTakePicture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            takePictureLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun handleImageUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                handleBitmap(bitmap)
            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read image URI", e)
            Toast.makeText(this, "Failed to read image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleBitmap(bitmap: Bitmap) {
        // Resize and compress
        val maxDim = 300
        val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val (w, h) = if (ratio > 1) {
                Pair(maxDim, (maxDim / ratio).toInt())
            } else {
                Pair((maxDim * ratio).toInt(), maxDim)
            }
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }

        val baos = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val bytes = baos.toByteArray()
        val base64Str = Base64.encodeToString(bytes, Base64.DEFAULT)

        base64ImageString = base64Str

        // Display updated preview in circular imageView
        AvatarHelper.setAvatar(
            imageView = binding.ivProfilePicture,
            name = binding.etName.text.toString(),
            base64Str = base64Str,
            fallbackTextView = binding.tvAvatarLarge
        )
    }

    private fun saveChanges() {
        val authUser = auth.currentUser ?: return
        val uid = authUser.uid
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        // Validation
        if (name.isEmpty()) {
            binding.nameInputLayout.error = "Name is required"
            return
        } else {
            binding.nameInputLayout.error = null
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = "Enter a valid email address"
            return
        } else {
            binding.emailInputLayout.error = null
        }

        val updatePassword = password.isNotEmpty()
        if (updatePassword) {
            if (password.length < 6) {
                binding.passwordInputLayout.error = "Password must be at least 6 characters"
                return
            } else {
                binding.passwordInputLayout.error = null
            }

            if (password != confirmPassword) {
                binding.confirmPasswordInputLayout.error = "Passwords do not match"
                return
            } else {
                binding.confirmPasswordInputLayout.error = null
            }
        }

        setLoading(true)

        // Helper to update database user and firebase auth profile
        val completeProfileSave = {
            val updates = mutableMapOf<String, Any>()
            updates["fullName"] = name
            updates["email"] = email
            base64ImageString?.let {
                updates["profilePicture"] = it
            }

            // Sync DisplayName to Auth user
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()

            authUser.updateProfile(profileUpdates)
                .addOnCompleteListener { authProfileTask ->
                    if (authProfileTask.isSuccessful) {
                        Log.d(TAG, "Auth display name synced successfully")
                    } else {
                        Log.w(TAG, "Failed to sync Auth display name: ${authProfileTask.exception?.message}")
                    }
                    
                    // Update Database Record
                    db.child("users").child(uid).updateChildren(updates)
                        .addOnSuccessListener {
                            setLoading(false)
                            Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                            // Set results and finish
                            val intent = Intent()
                            setResult(RESULT_OK, intent)
                            finish()
                        }
                        .addOnFailureListener { e ->
                            setLoading(false)
                            Log.e(TAG, "Failed to update user record in DB: ${e.message}")
                            Toast.makeText(this, "Failed to save database record: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                }
        }

        // Execute credential updates
        if (email != authUser.email) {
            authUser.updateEmail(email)
                .addOnSuccessListener {
                    Log.d(TAG, "Auth email updated successfully")
                    if (updatePassword) {
                        authUser.updatePassword(password)
                            .addOnSuccessListener {
                                Log.d(TAG, "Auth password updated successfully")
                                completeProfileSave()
                            }
                            .addOnFailureListener { e ->
                                setLoading(false)
                                Log.e(TAG, "Auth password update failed", e)
                                Toast.makeText(this, "Password update failed. Try logging in again. Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        completeProfileSave()
                    }
                }
                .addOnFailureListener { e ->
                    setLoading(false)
                    Log.e(TAG, "Auth email update failed", e)
                    Toast.makeText(this, "Email update failed. Sensitive changes require recent login. Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else if (updatePassword) {
            authUser.updatePassword(password)
                .addOnSuccessListener {
                    Log.d(TAG, "Auth password updated successfully")
                    completeProfileSave()
                }
                .addOnFailureListener { e ->
                    setLoading(false)
                    Log.e(TAG, "Auth password update failed", e)
                    Toast.makeText(this, "Password update failed. Sensitive changes require recent login. Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            completeProfileSave()
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.flLoadingOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !isLoading
        binding.flAvatarContainer.isEnabled = !isLoading
    }
}
