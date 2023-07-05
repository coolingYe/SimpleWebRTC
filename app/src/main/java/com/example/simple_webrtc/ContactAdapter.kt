package com.example.simple_webrtc

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ContactAdapter(private var contactData: List<String>) : RecyclerView.Adapter<ContactAdapter.MyHolder>() {

    var setOnClickListener: ((View, String) -> Unit)? = null

    class MyHolder(itemView: View?) : RecyclerView.ViewHolder(itemView!!) {
        val title: TextView = itemView?.findViewById(R.id.tv_timer_plan_title)!!
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyHolder {
        return MyHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_contact_plan, parent, false))
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: MyHolder, position: Int) {
        holder.title.text = contactData[position]
        holder.itemView.setOnClickListener { v ->
            setOnClickListener?.invoke(v, contactData[position])
        }
    }

    override fun getItemCount() = contactData.size


    @SuppressLint("NotifyDataSetChanged")
    fun updateList(data: List<String>) {
        this.contactData = data
        notifyDataSetChanged()
    }

}
