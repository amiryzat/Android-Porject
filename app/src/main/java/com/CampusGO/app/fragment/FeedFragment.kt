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
import com.CampusGO.app.ChatActivity
import com.CampusGO.app.CreateTaskActivity
import com.CampusGO.app.R
import com.CampusGO.app.TaskDetailActivity
import com.CampusGO.app.adapter.TaskFeedAdapter
import com.CampusGO.app.model.Task
import com.CampusGO.app.model.TaskStatus
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FeedFragment : Fragment() {

    private lateinit var rvTasks: RecyclerView
    private lateinit var tvTaskCount: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var fabPost: FloatingActionButton
    private lateinit var chipGroupFilters: ChipGroup

    private lateinit var adapter: TaskFeedAdapter
    private var allTasks = mutableListOf<Task>()
    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private var tasksListener: ValueEventListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_feed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvTasks = view.findViewById(R.id.rvTasks)
        tvTaskCount = view.findViewById(R.id.tvTaskCount)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        fabPost = view.findViewById(R.id.fabPost)
        chipGroupFilters = view.findViewById(R.id.chipGroupFilters)

        adapter = TaskFeedAdapter(
            emptyList(),
            onAccept = { task -> openTaskDetail(task) },
            onChat = { task -> openChat(task) }
        )

        rvTasks.layoutManager = LinearLayoutManager(requireContext())
        rvTasks.adapter = adapter

        fabPost.setOnClickListener {
            startActivity(Intent(requireContext(), CreateTaskActivity::class.java))
        }

        chipGroupFilters.setOnCheckedStateChangeListener { _, _ -> applyFilter() }

        loadTasks()
    }

    private fun loadTasks() {
        tasksListener?.let { db.child("tasks").removeEventListener(it) }
        tasksListener = db.child("tasks").orderByChild("createdAt").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                allTasks.clear()
                val currentUid = auth.currentUser?.uid
                for (child in snapshot.children) {
                    val task = child.getValue(Task::class.java) ?: continue
                    if (task.status == TaskStatus.OPEN && task.posterId != currentUid) {
                        allTasks.add(0, task)
                    }
                }
                applyFilter()
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("FeedFragment", "DB error: code=${error.code} msg=${error.message}")
            }
        })
    }

    private fun applyFilter() {
        if (!isAdded) return
        val checkedId = chipGroupFilters.checkedChipId
        val filtered = when (checkedId) {
            R.id.chipEmergency -> allTasks.filter { it.isEmergency }
            R.id.chipHighest -> allTasks.sortedByDescending { it.price }
            R.id.chipNewest -> allTasks.sortedByDescending { it.createdAt }
            else -> allTasks.toList()
        }
        adapter.updateTasks(filtered)
        tvTaskCount.text = "${filtered.size} tasks live"
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        rvTasks.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openTaskDetail(task: Task) {
        startActivity(Intent(requireContext(), TaskDetailActivity::class.java).apply {
            putExtra("taskId", task.id)
        })
    }

    private fun openChat(task: Task) {
        startActivity(Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("taskId", task.id)
            putExtra("chatId", "${task.id}_${auth.currentUser?.uid}")
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tasksListener?.let { db.child("tasks").removeEventListener(it) }
    }
}
