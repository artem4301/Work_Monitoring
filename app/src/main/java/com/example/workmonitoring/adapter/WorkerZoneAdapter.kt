package com.example.workmonitoring.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.workmonitoring.R
import com.example.workmonitoring.model.User

class WorkerZoneAdapter(
    private val onWorkerClick: (User) -> Unit
) : RecyclerView.Adapter<WorkerZoneAdapter.WorkerViewHolder>() {

    private var workers: List<User> = emptyList()

    fun submitList(list: List<User>) {
        workers = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_worker, parent, false)
        return WorkerViewHolder(view, onWorkerClick)
    }

    override fun onBindViewHolder(holder: WorkerViewHolder, position: Int) {
        holder.bind(workers[position])
    }

    override fun getItemCount(): Int = workers.size

    class WorkerViewHolder(itemView: View, private val onClick: (User) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val textName: TextView = itemView.findViewById(R.id.textWorkerName)
        private val textZone: TextView = itemView.findViewById(R.id.textWorkerZone)

        fun bind(user: User) {
            textName.text = "${user.lastName} ${user.firstName}"
            textZone.text = user.workZoneAddress ?: "Зона не выбрана"

            itemView.setOnClickListener {
                onClick(user)
            }
        }
    }
}
