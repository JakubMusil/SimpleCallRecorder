package com.simplecallrecorder.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.simplecallrecorder.R
import com.simplecallrecorder.data.Recording
import com.simplecallrecorder.databinding.ItemRecordingBinding
import java.text.SimpleDateFormat
import java.util.Locale

class RecordingsAdapter(
    private val onUploadClick: (Recording) -> Unit,
    private val onDeleteClick: (Recording) -> Unit
) : ListAdapter<Recording, RecordingsAdapter.RecordingViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
        val binding = ItemRecordingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RecordingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecordingViewHolder(
        private val binding: ItemRecordingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recording: Recording) {
            binding.tvFileName.text = recording.fileName
            binding.tvPhoneNumber.text = recording.phoneNumber
            binding.tvDateTime.text = formatDateTime(recording.dateTime)
            binding.tvDuration.text = formatDuration(recording.durationSeconds)
            binding.tvCallType.text = recording.callType.replaceFirstChar {
                it.titlecase(Locale.getDefault())
            }

            if (recording.isUploaded) {
                binding.btnUpload.setText(R.string.uploaded)
                binding.btnUpload.isEnabled = false
            } else {
                binding.btnUpload.setText(R.string.upload_to_drive)
                binding.btnUpload.isEnabled = true
                binding.btnUpload.setOnClickListener { onUploadClick(recording) }
            }

            binding.btnDelete.setOnClickListener { onDeleteClick(recording) }
        }

        private fun formatDateTime(dateTime: String): String {
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                val date = inputFormat.parse(dateTime)
                if (date != null) outputFormat.format(date) else dateTime
            } catch (e: Exception) {
                dateTime
            }
        }

        private fun formatDuration(seconds: Long): String {
            val minutes = seconds / 60
            val secs = seconds % 60
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Recording>() {
        override fun areItemsTheSame(oldItem: Recording, newItem: Recording) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Recording, newItem: Recording) =
            oldItem == newItem
    }
}
