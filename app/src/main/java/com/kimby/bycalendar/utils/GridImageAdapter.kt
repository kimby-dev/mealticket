package com.kimby.bycalendar.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class GridImageAdapter(
    private var imageList: List<Uri>,
    private val context: Context,
    private val onDeleteRequest: (Uri) -> Unit,
    private val onMoveRequest: (Uri) -> Unit
) : RecyclerView.Adapter<GridImageAdapter.GridViewHolder>() {

    inner class GridViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
        val size = parent.measuredWidth / 5
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        return GridViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
        val uri = imageList[position]
        Glide.with(context)
            .load(uri.path?.let { File(it) })
            .into(holder.imageView)

        holder.imageView.setOnLongClickListener {
            AlertDialog.Builder(context)
                .setTitle("사진 작업")
                .setItems(arrayOf("다른 날짜로 이동", "삭제")) { _, which ->
                    when (which) {
                        0 -> onMoveRequest(uri)   // 이동
                        1 -> onDeleteRequest(uri) // 삭제
                    }
                }
                .show()
            true
        }
    }

    override fun getItemCount(): Int = imageList.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateImages(newImages: List<Uri>) {
        imageList = newImages
        notifyDataSetChanged()
    }
}