package com.example.simple_webrtc

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.simple_webrtc.model.Contact

class ContactAdapter(private var contactData: List<Contact>) : RecyclerView.Adapter<ContactAdapter.MyHolder>() {

    var setOnClickListener: ((View, Contact) -> Unit)? = null
    var setOnLongClickListener: ((View, Contact) -> Unit)? = null

    class MyHolder(itemView: View?) : RecyclerView.ViewHolder(itemView!!) {
        val title: TextView = itemView?.findViewById(R.id.tv_contact_title)!!
        val desc: TextView = itemView?.findViewById(R.id.tv_contact_desc)!!
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyHolder {
        return MyHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_contact, parent, false))
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: MyHolder, position: Int) {
        holder.title.text = contactData[position].name
        holder.desc.text = contactData[position].ipAddress
        holder.itemView.setOnClickListener { v ->
            setOnClickListener?.invoke(v, contactData[position])
        }
        holder.itemView.setOnLongClickListener{
            setOnLongClickListener?.invoke(it, contactData[position])
            true
        }
    }

    override fun getItemCount() = contactData.size


    @SuppressLint("NotifyDataSetChanged")
    fun updateList(data: List<Contact>) {
        this.contactData = data
        notifyDataSetChanged()
    }

}
