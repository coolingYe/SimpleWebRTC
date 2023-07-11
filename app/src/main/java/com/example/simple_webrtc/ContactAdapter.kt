package com.example.simple_webrtc

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.simple_webrtc.model.Contact

class ContactAdapter(private var contactData: List<Contact>) : RecyclerView.Adapter<ContactAdapter.MyHolder>() {

    var setOnClickListener: ((View, Contact) -> Unit)? = null
    var setOnLongClickListener: ((View, Contact) -> Unit)? = null

    class MyHolder(itemView: View?) : RecyclerView.ViewHolder(itemView!!) {
        val title: TextView = itemView?.findViewById(R.id.tv_contact_title)!!
        val desc: TextView = itemView?.findViewById(R.id.tv_contact_desc)!!
        val img: ImageView = itemView?.findViewById(R.id.img_state)!!
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyHolder {
        return MyHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_contact, parent, false))
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: MyHolder, position: Int) {
        holder.title.text = contactData[position].name
        holder.desc.text = contactData[position].ipAddress
        holder.img.setImageBitmap(getStateImage(contactData[position]))

        holder.itemView.setOnClickListener { v ->
            setOnClickListener?.invoke(v, contactData[position])
        }
        holder.itemView.setOnLongClickListener{
            setOnLongClickListener?.invoke(it, contactData[position])
            true
        }
    }

    override fun getItemCount() = contactData.size

    fun getStateImage(contact: Contact): Bitmap {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = Paint()
        p.color = when (contact.state) {
            Contact.State.CONTACT_ONLINE -> Color.parseColor("#00ff0a") // green
            Contact.State.CONTACT_OFFLINE -> Color.parseColor("#ff0000") // red
            Contact.State.NETWORK_UNREACHABLE -> Color.parseColor("#f25400") // light orange
            Contact.State.APP_NOT_RUNNING -> Color.parseColor("#ff7000") // orange
            Contact.State.AUTHENTICATION_FAILED -> Color.parseColor("#612c00") // brown
            Contact.State.COMMUNICATION_FAILED -> Color.parseColor("#808080") // grey
        }
        canvas.drawCircle(100f, 100f, 100f, p)
        return bitmap
    }


    @SuppressLint("NotifyDataSetChanged")
    fun updateList(data: List<Contact>) {
        this.contactData = data
        notifyDataSetChanged()
    }

}
