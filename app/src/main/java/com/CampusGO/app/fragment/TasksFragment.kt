package com.CampusGO.app.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.CampusGO.app.R
import com.CampusGO.app.TaskTrackingActivity
import com.CampusGO.app.adapter.MyTaskAdapter
import com.CampusGO.app.model.Task
import com.CampusGO.app.model.TaskStatus
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class TasksFragment : Fragment() {

    private lateinit var rvMyTasks: RecyclerView
    private lateinit var tvStats: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var chipGroupTabs: ChipGroup

    private lateinit var adapter: MyTaskAdapter
    private var postedTasks = mutableListOf<Task>()
    private var acceptedTasks = mutableListOf<Task>()
    private var completedTasks = mutableListOf<Task>()

    // Lazy init avoids any class-level Firebase init ordering issues
    private val db by lazy { FirebaseDatabase.getInstance().reference }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private var listener: ValueEventListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Log.d("TasksFragment", "onCreateView")
        return inflater.inflate(R.layout.fragment_tasks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("TasksFragment", "onViewCreated")

        rvMyTasks = view.findViewById(R.id.rvMyTasks)
        tvStats = view.findViewById(R.id.tvStats)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        chipGroupTabs = view.findViewById(R.id.chipGroupTabs)

        adapter = MyTaskAdapter(emptyList()) { task ->
            if (!isAdded) return@MyTaskAdapter
            startActivity(Intent(requireContext(), TaskTrackingActivity::class.java).apply {
                putExtra("taskId", task.id)
            })
        }

        rvMyTasks.layoutManager = LinearLayoutManager(requireContext())
        rvMyTasks.adapter = adapter

        chipGroupTabs.setOnCheckedStateChangeListener { _, _ ->
            if (isAdded) updateDisplay()
        }

        loadMyTasks()
    }

    override fun onResume() {
        super.onResume()

        if (::adapter.isInitialized) {
            loadMyTasks()
        }
    }

    private fun loadMyTasks() {
        val uid = auth.currentUser?.uid ?: run {
            Log.w("TasksFragment", "loadMyTasks: no current user")
            return
        }

        Log.d("TasksFragment", "loadMyTasks: uid=$uid")

        listener?.let {
            db.child("tasks").removeEventListener(it)
        }

        listener = db.child("tasks").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                Log.d("TasksFragment", "onDataChange: ${snapshot.childrenCount} total tasks")

                val v = view ?: return
                if (!isAdded) return

                postedTasks.clear()
                acceptedTasks.clear()
                completedTasks.clear()

                for (child in snapshot.children) {
                    if (child.key == "_placeholder") continue

                    val task = try {
                        child.getValue(Task::class.java)
                    } catch (e: Exception) {
                        Log.e("TasksFragment", "Invalid task data at ${child.key}", e)
                        null
                    } ?: continue

                    // Important: if old tasks do not have id inside the object,
                    // use the Firebase key as the task id.
                    if (task.id.isBlank()) {
                        task.id = child.key ?: ""
                    }

                    val status = task.status.trim().uppercase()

                    Log.d(
                        "TasksFragment",
                        "Task check: id=${task.id}, status=$status, posterId=${task.posterId}, runnerId=${task.runnerId}, currentUid=$uid"
                    )

                    when {
                        task.posterId == uid &&
                                status != TaskStatus.COMPLETED &&
                                status != TaskStatus.CANCELLED -> {
                            postedTasks.add(0, task)
                        }

                        task.runnerId == uid &&
                                (
                                        status == TaskStatus.ACCEPTED ||
                                                status == TaskStatus.ON_THE_WAY ||
                                                status == TaskStatus.DELIVERED
                                        ) -> {
                            acceptedTasks.add(0, task)
                        }

                        (task.posterId == uid || task.runnerId == uid) &&
                                (
                                        status == TaskStatus.COMPLETED ||
                                                status == TaskStatus.CANCELLED
                                        ) -> {
                            completedTasks.add(0, task)
                        }
                    }
                }

                Log.d(
                    "TasksFragment",
                    "parsed: posted=${postedTasks.size}, accepted=${acceptedTasks.size}, completed=${completedTasks.size}"
                )

                v.post {
                    if (isAdded) updateDisplay()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TasksFragment", "DB cancelled: code=${error.code} msg=${error.message}")
            }
        })
    }

    private fun updateDisplay() {
        val v = view ?: return  // guard against view being null
        if (!isAdded) return
        Log.d("TasksFragment", "updateDisplay")
        val checkedId = chipGroupTabs.checkedChipId
        val list = when (checkedId) {
            R.id.chipPosted -> postedTasks.toList()
            R.id.chipCompleted -> completedTasks.toList()
            else -> acceptedTasks.toList()
        }
        adapter.updateTasks(list)
        val activeCount = acceptedTasks.size
        val doneCount = completedTasks.size
        tvStats.text = "$activeCount ACTIVE · $doneCount DONE"
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        rvMyTasks.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("TasksFragment", "onDestroyView")
        listener?.let { db.child("tasks").removeEventListener(it) }
    }
}
