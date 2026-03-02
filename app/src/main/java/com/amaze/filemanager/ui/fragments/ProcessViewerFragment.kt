/*
 * Copyright (C) 2014-2026 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.ui.fragments

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.amaze.filemanager.R
import com.amaze.filemanager.asynchronous.workers.AbstractProgressiveWorker
import com.amaze.filemanager.asynchronous.workers.WorkerInteractionBridge
import com.amaze.filemanager.asynchronous.workers.toDatapointParcelable
import com.amaze.filemanager.databinding.ProcessparentBinding
import com.amaze.filemanager.filesystem.files.FileUtils
import com.amaze.filemanager.ui.activities.MainActivity
import com.amaze.filemanager.ui.dialogs.GeneralDialogCreation
import com.amaze.filemanager.ui.theme.AppTheme
import com.amaze.filemanager.utils.DatapointParcelable
import com.amaze.filemanager.utils.Utils
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet

/**
 * Fragment that displays progress of ongoing file operations (copy, extract, compress,
 * encrypt, decrypt).
 *
 * Observes WorkManager [WorkInfo] via LiveData for all progressive workers.
 */
class ProcessViewerFragment : Fragment() {
    companion object {
        private const val SERVICE_COPY = 0
    }

    private var isInitialized = false
    private var mainActivity: MainActivity? = null
    private var accentColor = 0
    private val lineData = LineData()
    private var binding: ProcessparentBinding? = null

    /** Time in seconds just for showing to the user. No guarantees. */
    private var looseTimeInSeconds = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = ProcessparentBinding.inflate(inflater)
        val rootView = binding!!.root

        mainActivity = activity as? MainActivity

        accentColor = mainActivity!!.accent
        if (mainActivity!!.appTheme == AppTheme.DARK || mainActivity!!.appTheme == AppTheme.BLACK) {
            rootView.setBackgroundResource(R.color.cardView_background)
        }

        if (mainActivity!!.appTheme == AppTheme.DARK || mainActivity!!.appTheme == AppTheme.BLACK) {
            binding!!.deleteButton.setImageResource(R.drawable.ic_action_cancel)
            binding!!.cardView.setCardBackgroundColor(
                Utils.getColor(context, R.color.cardView_foreground),
            )
            binding!!.cardView.cardElevation = 0f
        }

        return rootView
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        mainActivity!!.appbar.setTitle(R.string.process_viewer)
        mainActivity!!.hideFab()
        mainActivity!!.appbar.bottomBar.setVisibility(View.GONE)
        mainActivity!!.supportInvalidateOptionsMenu()

        val skinColor = mainActivity!!.currentColorPreference.primaryFirstTab
        val skinTwoColor = mainActivity!!.currentColorPreference.primarySecondTab
        accentColor = mainActivity!!.accent

        mainActivity!!.updateViews(
            ColorDrawable(if (MainActivity.currentTab == 1) skinTwoColor else skinColor),
        )

        // Observe WorkManager for all progressive workers (including copy)
        observeWorkers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        mainActivity = null
    }

    private fun observeWorkers() {
        val workManager = WorkManager.getInstance(requireContext())
        val workInfos =
            workManager.getWorkInfosByTagLiveData(
                AbstractProgressiveWorker.TAG_PROGRESSIVE_WORK,
            )

        workInfos.observe(viewLifecycleOwner) { workInfoList ->
            for (info in workInfoList) {
                if (info.state == WorkInfo.State.RUNNING) {
                    val data = info.progress
                    val serviceType = data.getInt(AbstractProgressiveWorker.KEY_SERVICE_TYPE, -1)
                    if (serviceType == -1) continue

                    val needsPassword =
                        data.getBoolean(
                            AbstractProgressiveWorker.KEY_NEEDS_PASSWORD,
                            false,
                        )
                    if (needsPassword) {
                        val archivePath =
                            data.getString(
                                AbstractProgressiveWorker.KEY_ARCHIVE_PATH,
                            )
                        showPasswordDialog(info.id, archivePath)
                        continue
                    }

                    val datapoint = data.toDatapointParcelable()
                    if (datapoint != null) {
                        processResults(datapoint, serviceType)
                    }
                }
            }
        }
    }

    private fun showPasswordDialog(
        workId: java.util.UUID,
        archivePath: String?,
    ) {
        val ctx = context ?: return
        val activity = mainActivity ?: return

        GeneralDialogCreation.showPasswordDialog(
            ctx,
            activity,
            activity.utilsProvider.appTheme,
            R.string.archive_password_prompt,
            R.string.authenticate_password,
            { dialog, _ ->
                val editText =
                    dialog.view.findViewById<
                        androidx.appcompat.widget.AppCompatEditText,
                    >(R.id.singleedittext_input)
                WorkerInteractionBridge.supplyPassword(workId, editText.text.toString())
                dialog.dismiss()
            },
            { dialog, _ ->
                dialog.dismiss()
                WorkerInteractionBridge.cancel(workId)
                WorkManager.getInstance(ctx).cancelWorkById(workId)
            },
        )
    }

    fun processResults(
        dataPackage: DatapointParcelable?,
        serviceType: Int,
    ) {
        if (binding == null) return

        if (dataPackage != null) {
            val name = dataPackage.name
            val total = dataPackage.totalSize
            val doneBytes = dataPackage.byteProgress
            val move = dataPackage.move

            if (!isInitialized) {
                chartInit(total)
                setupDrawables(serviceType, move)
                isInitialized = true
            }

            addEntry(
                FileUtils.readableFileSizeFloat(doneBytes),
                FileUtils.readableFileSizeFloat(dataPackage.speedRaw),
            )

            binding!!.textViewProgressFileName.text = name

            val bytesText =
                HtmlCompat.fromHtml(
                    resources.getString(R.string.written) +
                        " <font color='$accentColor'><i>" +
                        Formatter.formatFileSize(context, doneBytes) +
                        " </font></i>" +
                        resources.getString(R.string.out_of) +
                        " <i>" +
                        Formatter.formatFileSize(context, total) +
                        "</i>",
                    HtmlCompat.FROM_HTML_MODE_COMPACT,
                )
            binding!!.textViewProgressBytes.text = bytesText

            val fileProcessedSpan =
                HtmlCompat.fromHtml(
                    resources.getString(R.string.processing_file) +
                        " <font color='$accentColor'><i>" +
                        dataPackage.sourceProgress +
                        " </font></i>" +
                        resources.getString(R.string.of) +
                        " <i>" +
                        dataPackage.amountOfSourceFiles +
                        "</i>",
                    HtmlCompat.FROM_HTML_MODE_COMPACT,
                )
            binding!!.textViewProgressFile.text = fileProcessedSpan

            val speedSpan =
                HtmlCompat.fromHtml(
                    resources.getString(R.string.current_speed) +
                        ": <font color='$accentColor'><i>" +
                        Formatter.formatFileSize(context, dataPackage.speedRaw) +
                        "/s</font></i>",
                    HtmlCompat.FROM_HTML_MODE_COMPACT,
                )
            binding!!.textViewProgressSpeed.text = speedSpan

            val timerSpan =
                HtmlCompat.fromHtml(
                    resources.getString(R.string.service_timer) +
                        ": <font color='$accentColor'><i>" +
                        Utils.formatTimer(++looseTimeInSeconds) +
                        "</font></i>",
                    HtmlCompat.FROM_HTML_MODE_COMPACT,
                )
            binding!!.textViewProgressTimer.text = timerSpan

            if (dataPackage.completed) binding!!.deleteButton.visibility = View.GONE
        }
    }

    /** Setup drawables and click listeners based on the service type constants. */
    private fun setupDrawables(
        serviceType: Int,
        isMove: Boolean,
    ) {
        val isDarkTheme =
            mainActivity?.appTheme == AppTheme.DARK ||
                mainActivity?.appTheme == AppTheme.BLACK

        when (serviceType) {
            SERVICE_COPY -> {
                val iconRes =
                    if (isDarkTheme) {
                        R.drawable.ic_content_copy_white_36dp
                    } else {
                        R.drawable.ic_content_copy_grey600_36dp
                    }
                binding?.progressImage?.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), iconRes),
                )
                binding?.textViewProgressType?.text =
                    if (isMove) {
                        resources.getString(R.string.moving)
                    } else {
                        resources.getString(R.string.copying)
                    }
                cancelViaWorkManager()
            }
            AbstractProgressiveWorker.SERVICE_EXTRACT -> {
                val iconRes =
                    if (isDarkTheme) {
                        R.drawable.ic_zip_box_white
                    } else {
                        R.drawable.ic_zip_box_grey
                    }
                binding?.progressImage?.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), iconRes),
                )
                binding?.textViewProgressType?.text = resources.getString(R.string.extracting)
                cancelViaWorkManager()
            }
            AbstractProgressiveWorker.SERVICE_COMPRESS -> {
                val iconRes =
                    if (isDarkTheme) {
                        R.drawable.ic_zip_box_white
                    } else {
                        R.drawable.ic_zip_box_grey
                    }
                binding?.progressImage?.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), iconRes),
                )
                binding?.textViewProgressType?.text = resources.getString(R.string.compressing)
                cancelViaWorkManager()
            }
            AbstractProgressiveWorker.SERVICE_ENCRYPT -> {
                val iconRes =
                    if (isDarkTheme) {
                        R.drawable.ic_folder_lock_white_36dp
                    } else {
                        R.drawable.ic_folder_lock_grey600_36dp
                    }
                binding?.progressImage?.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), iconRes),
                )
                binding?.textViewProgressType?.text =
                    resources.getString(R.string.crypt_encrypting)
                cancelViaWorkManager()
            }
            AbstractProgressiveWorker.SERVICE_DECRYPT -> {
                val iconRes =
                    if (isDarkTheme) {
                        R.drawable.ic_folder_lock_open_white_36dp
                    } else {
                        R.drawable.ic_folder_lock_open_grey600_36dp
                    }
                binding?.progressImage?.setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), iconRes),
                )
                binding?.textViewProgressType?.text =
                    resources.getString(R.string.crypt_decrypting)
                cancelViaWorkManager()
            }
        }
    }

    /** Setup click listener to cancel via WorkManager. */
    private fun cancelViaWorkManager() {
        if (binding == null) return

        binding!!.deleteButton.setOnClickListener {
            Toast.makeText(
                activity,
                resources.getString(R.string.stopping),
                Toast.LENGTH_LONG,
            ).show()
            // Cancel all progressive work
            WorkManager.getInstance(requireContext())
                .cancelAllWorkByTag(AbstractProgressiveWorker.TAG_PROGRESSIVE_WORK)
            onCancelUI()
        }
    }

    private fun onCancelUI() {
        binding?.textViewProgressType?.text = resources.getString(R.string.cancelled)
        binding?.textViewProgressSpeed?.text = ""
        binding?.textViewProgressFile?.text = ""
        binding?.textViewProgressBytes?.text = ""
        binding?.textViewProgressFileName?.text = ""
        binding?.textViewProgressType?.setTextColor(
            Utils.getColor(context, android.R.color.holo_red_light),
        )
    }

    /**
     * Add a new entry dynamically to the chart.
     *
     * @param xValue the x-axis value, the number of bytes processed till now
     * @param yValue the y-axis value, bytes processed per sec
     */
    private fun addEntry(
        xValue: Float,
        yValue: Float,
    ) {
        var dataSet: ILineDataSet? = lineData.getDataSetByIndex(0)

        if (dataSet == null) {
            dataSet = createDataSet()
            lineData.addDataSet(dataSet)
        }

        dataSet.addEntry(Entry(xValue, yValue))
        lineData.notifyDataChanged()
        binding?.progressChart?.notifyDataSetChanged()
        binding?.progressChart?.invalidate()
    }

    /** Creates an instance for [LineDataSet] which will store the entries. */
    private fun createDataSet(): LineDataSet {
        return LineDataSet(ArrayList<Entry>(), null).apply {
            lineWidth = 1.75f
            circleRadius = 5f
            circleHoleRadius = 2.5f
            color = Color.WHITE
            setCircleColor(Color.WHITE)
            highLightColor = Color.WHITE
            setDrawValues(false)
            circleHoleColor = accentColor
        }
    }

    /**
     * Initialize chart for the first time.
     *
     * @param totalBytes maximum value for x-axis
     */
    private fun chartInit(totalBytes: Long) {
        binding?.progressChart?.apply {
            setBackgroundColor(accentColor)
            legend.isEnabled = false
            description.isEnabled = false

            val xAxis = this.xAxis
            val yAxisLeft = axisLeft
            axisRight.isEnabled = false
            yAxisLeft.textColor = Color.WHITE
            yAxisLeft.axisLineColor = Color.TRANSPARENT
            yAxisLeft.typeface = Typeface.DEFAULT_BOLD
            yAxisLeft.gridColor = Utils.getColor(context, R.color.white_translucent)

            xAxis.axisMaximum = FileUtils.readableFileSizeFloat(totalBytes)
            xAxis.axisMinimum = 0.0f
            xAxis.axisLineColor = Color.TRANSPARENT
            xAxis.gridColor = Color.TRANSPARENT
            xAxis.textColor = Color.WHITE
            xAxis.typeface = Typeface.DEFAULT_BOLD
            data = lineData
            invalidate()
        }
    }
}
