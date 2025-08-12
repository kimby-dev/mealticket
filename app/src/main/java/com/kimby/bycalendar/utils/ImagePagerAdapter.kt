package com.kimby.bycalendar.utils

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import com.github.chrisbanes.photoview.PhotoView

class ImagePagerAdapter(
    private var imageList: List<Uri>,
    private val context: Context,
    private val longClickListener: OnPhotoLongClickListener
) : RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {

    interface OnPhotoLongClickListener {
        fun onPhotoLongClicked(uri: Uri)
    }

    inner class ImageViewHolder(val photoView: PhotoView) : RecyclerView.ViewHolder(photoView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val photoView = PhotoView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        return ImageViewHolder(photoView)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val uri = imageList[position] // ✅ 여기서 uri 선언

        Glide.with(context)
            .load(imageList[position].path?.let { File(it) })
            .into(holder.photoView)

        // Long click 처리 추가
        holder.photoView.setOnLongClickListener {
            longClickListener.onPhotoLongClicked(uri)
            true
        }
    }

    override fun getItemCount(): Int = imageList.size

    fun updateImages(newImages: List<Uri>) {
        imageList = newImages
        notifyDataSetChanged()
    }
}
