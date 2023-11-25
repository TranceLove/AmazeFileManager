/*
 * Copyright (C) 2014-2024 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
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

import android.animation.ArgbEvaluator
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.amaze.filemanager.R
import com.amaze.filemanager.application.AppConfig
import com.amaze.filemanager.database.TabHandler
import com.amaze.filemanager.database.models.explorer.Tab
import com.amaze.filemanager.databinding.TabfragmentBinding
import com.amaze.filemanager.fileoperations.filesystem.OpenMode
import com.amaze.filemanager.ui.ColorCircleDrawable
import com.amaze.filemanager.ui.activities.MainActivity
import com.amaze.filemanager.ui.dialogs.GeneralDialogCreation
import com.amaze.filemanager.ui.drag.DragToTrashListener
import com.amaze.filemanager.ui.drag.TabFragmentSideDragListener
import com.amaze.filemanager.ui.fragments.data.TabFragmentViewModel
import com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants
import com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_CURRENT_TAB
import com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_SAVED_PATHS
import com.amaze.filemanager.ui.hideFade
import com.amaze.filemanager.ui.showFade
import com.amaze.filemanager.ui.views.Indicator
import com.amaze.filemanager.utils.DataUtils
import com.amaze.filemanager.utils.PreferenceUtils.DEFAULT_CURRENT_TAB
import com.amaze.filemanager.utils.PreferenceUtils.DEFAULT_SAVED_PATHS
import com.amaze.filemanager.utils.Utils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TabFragment : Fragment() {
    private var savePaths = false

    private lateinit var sectionsPagerAdapter: ScreenSlidePagerAdapter

    private lateinit var sharedPrefs: SharedPreferences

    /** ink indicators for viewpager only for Lollipop+  */
    private var indicator: Indicator? = null

    /** views for circular drawables below android lollipop  */
    private var circleDrawable1: AppCompatImageView? = null
    private var circleDrawable2: AppCompatImageView? = null

    /** color drawable for action bar background  */
    private val colorDrawable = ColorDrawable()

    /** colors relative to current visible tab  */
    @ColorInt
    private var startColor = 0

    @ColorInt
    private var endColor = 0

    private val evaluator = ArgbEvaluator()

    var dragPlaceholder: ConstraintLayout? = null
        private set

    private lateinit var viewBinding: TabfragmentBinding
    private lateinit var viewPager: ViewPager2

    private val viewModel: TabFragmentViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        viewBinding = TabfragmentBinding.inflate(inflater, container, false)
        dragPlaceholder = viewBinding.dragPlaceholder.root

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            indicator = requireActivity().findViewById(R.id.indicator)
        } else {
            circleDrawable1 = requireActivity().findViewById(R.id.tab_indicator1)
            circleDrawable2 = requireActivity().findViewById(R.id.tab_indicator2)
        }

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireActivity())
        savePaths = sharedPrefs.getBoolean(PREFERENCE_SAVED_PATHS, DEFAULT_SAVED_PATHS)

        viewPager = viewBinding.pager

        val hideFab = arguments?.getBoolean(MainFragment.BUNDLE_HIDE_FAB) ?: false

        requireMainActivity().supportInvalidateOptionsMenu()
        viewPager.registerOnPageChangeCallback(OnPageChangeCallbackImpl())

        sectionsPagerAdapter = ScreenSlidePagerAdapter(requireActivity())
        if (savedInstanceState == null) {
            val lastOpenTab = sharedPrefs.getInt(PREFERENCE_CURRENT_TAB, DEFAULT_CURRENT_TAB)
            MainActivity.currentTab = lastOpenTab

            refactorDrawerStorages(true, hideFab)

            viewPager.adapter = sectionsPagerAdapter

            try {
                viewPager.setCurrentItem(lastOpenTab, true)
                if (circleDrawable1 != null && circleDrawable2 != null) {
                    updateIndicator(viewPager.currentItem)
                }
            } catch (e: Exception) {
                LOG.warn("failed to set current viewpager item", e)
            }
        } else {
            viewModel.clearFragmentList()
            try {
                viewModel.addFragment(0, parentFragmentManager.getFragment(savedInstanceState, KEY_FRAGMENT_0)!!)
                viewModel.addFragment(1, parentFragmentManager.getFragment(savedInstanceState, KEY_FRAGMENT_1)!!)
            } catch (e: Exception) {
                LOG.warn("failed to clear fragments", e)
            }

            sectionsPagerAdapter = ScreenSlidePagerAdapter(requireActivity())

            viewPager.adapter = sectionsPagerAdapter
            val pos1 = savedInstanceState.getInt(KEY_POSITION, 0)
            MainActivity.currentTab = pos1
            viewPager.currentItem = pos1
            sectionsPagerAdapter.notifyDataSetChanged()
        }

        if (indicator != null) indicator!!.setViewPager(viewPager)

        val userColorPreferences = requireMainActivity().currentColorPreference

        // color of viewpager when current tab is 0
        startColor = userColorPreferences.primaryFirstTab
        // color of viewpager when current tab is 1
        endColor = userColorPreferences.primarySecondTab

    /*
     TODO
    //update the views as there is any change in {@link MainActivity#currentTab}
    //probably due to config change
    colorDrawable.setColor(Color.parseColor(MainActivity.currentTab==1 ?
            ThemedActivity.skinTwo : ThemedActivity.skin));
    mainActivity.updateViews(colorDrawable);
    */
        return viewBinding.root
    }

    override fun onDestroyView() {
        indicator = null // Free the strong reference
        sharedPrefs.edit().putInt(PREFERENCE_CURRENT_TAB, MainActivity.currentTab).apply()
        super.onDestroyView()
    }

    fun updatePaths(pos: Int) {
        // Getting old path from database before clearing
        val tabHandler = TabHandler.getInstance()

        var i = 1
        for (fragment in viewModel.getFragments()) {
            if (fragment is MainFragment) {
                if (fragment.mainFragmentViewModel != null && i - 1 == MainActivity.currentTab && i == pos) {
                    updateBottomBar(fragment)
                    requireMainActivity()
                        .drawer
                        .selectCorrectDrawerItemForPath(fragment.currentPath)
                    if (fragment.mainFragmentViewModel!!.openMode == OpenMode.FILE) {
                        tabHandler.update(
                            Tab(
                                i,
                                fragment.currentPath,
                                fragment.mainFragmentViewModel!!.home
                            )
                        )
                    } else {
                        tabHandler.update(
                            Tab(
                                i,
                                fragment.mainFragmentViewModel!!.home,
                                fragment.mainFragmentViewModel!!.home
                            )
                        )
                    }
                }
                i++
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        sharedPrefs.edit().putInt(PREFERENCE_CURRENT_TAB, MainActivity.currentTab).apply()

        if (viewModel.getFragments().isNotEmpty()) {
            parentFragmentManager.executePendingTransactions()
            parentFragmentManager.putFragment(outState, KEY_FRAGMENT_0, viewModel.getFragment(0))
            parentFragmentManager.putFragment(outState, KEY_FRAGMENT_1, viewModel.getFragment(1))
            outState.putInt(KEY_POSITION, viewPager.currentItem)
        }
    }

    fun setPagingEnabled(isPaging: Boolean) {
        viewPager.isUserInputEnabled = isPaging
    }

    fun setCurrentItem(index: Int) {
        viewPager.currentItem = index
    }

    private inner class OnPageChangeCallbackImpl : OnPageChangeCallback() {
        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) {
            val mainFragment = requireMainActivity().currentMainFragment
            if (mainFragment == null || mainFragment.mainFragmentViewModel == null || mainFragment.mainActivity!!.listItemSelected) {
                return  // we do not want to update toolbar colors when ActionMode is activated
            }

            // during the config change
            @ColorInt val color =
                evaluator.evaluate(position + positionOffset, startColor, endColor) as Int

            colorDrawable.color = color
            requireMainActivity().updateViews(colorDrawable)
        }

        override fun onPageSelected(p1: Int) {
            requireMainActivity()
                .appbar
                .appbarLayout
                .animate()
                .translationY(0f)
                .setInterpolator(DecelerateInterpolator(2f))
                .start()

            MainActivity.currentTab = p1

            sharedPrefs.edit()?.putInt(PREFERENCE_CURRENT_TAB, MainActivity.currentTab)?.apply()

            val fragment = viewModel.getFragment(p1)
            if (fragment is MainFragment) {
                if (fragment.currentPath != null) {
                    requireMainActivity().drawer.selectCorrectDrawerItemForPath(fragment.currentPath)
                    updateBottomBar(fragment)
                    // FAB might be hidden in the previous tab
                    // so we check if it should be shown for the new tab
                    requireMainActivity().showFab()
                }
            }

            if (circleDrawable1 != null && circleDrawable2 != null) updateIndicator(p1)
        }

        override fun onPageScrollStateChanged(state: Int) = Unit
    }

    private inner class ScreenSlidePagerAdapter(fragmentActivity: FragmentActivity) :
        FragmentStateAdapter(fragmentActivity) {
        override fun getItemCount(): Int = viewModel.getFragments().size
        override fun createFragment(position: Int): Fragment = viewModel.getFragment(position)
    }

    private fun addNewTab(num: Int, path: String) {
        addTab(Tab(num, path, path), "", false)
    }

    /**
     * Fetches new storage paths from drawer and apply to tabs This method will just create tabs in UI
     * change paths in database. Calls should implement updating each tab's list for new paths.
     *
     * @param addTab whether new tabs should be added to ui or just change values in database
     * @param hideFabInCurrentMainFragment whether the FAB should be hidden in the current [     ]
     */
    fun refactorDrawerStorages(addTab: Boolean, hideFabInCurrentMainFragment: Boolean) {
        val tabHandler = TabHandler.getInstance()
        val tab1 = tabHandler.findTab(1)
        val tab2 = tabHandler.findTab(2)
        val tabs = tabHandler.allTabs
        val firstTabPath = requireMainActivity().drawer.firstPath
        val secondTabPath = requireMainActivity().drawer.secondPath

        if (tabs == null || tabs.size < 1 || tab1 == null || tab2 == null) {
            // creating tabs in db for the first time, probably the first launch of
            // app, or something got corrupted
            val currentFirstTab = if (Utils.isNullOrEmpty(firstTabPath)) "/" else firstTabPath
            val currentSecondTab =
                if (Utils.isNullOrEmpty(secondTabPath)) firstTabPath else secondTabPath
            if (addTab) {
                addNewTab(1, currentSecondTab)
                addNewTab(2, currentFirstTab)
            }
            tabHandler.addTab(Tab(1, currentSecondTab, currentSecondTab)).blockingAwait()
            tabHandler.addTab(Tab(2, currentFirstTab, currentFirstTab)).blockingAwait()

            if (currentFirstTab.equals("/", ignoreCase = true)) {
                sharedPrefs.edit().putBoolean(PreferencesConstants.PREFERENCE_ROOTMODE, true)
                    .apply()
            }
        } else {
            val path = requireArguments().getString(KEY_PATH, null)
            if (path != null && path.isNotEmpty()) {
                if (MainActivity.currentTab == 0) {
                    addTab(tab1, path, hideFabInCurrentMainFragment)
                    addTab(tab2, "", false)
                }

                if (MainActivity.currentTab == 1) {
                    addTab(tab1, "", false)
                    addTab(tab2, path, hideFabInCurrentMainFragment)
                }
            } else {
                addTab(tab1, "", false)
                addTab(tab2, "", false)
            }
        }
    }

    private fun addTab(tab: Tab, path: String?, hideFabInTab: Boolean) {
        val main = MainFragment()
        val b = Bundle()

        if (!path.isNullOrEmpty()) {
            b.putString("lastpath", path)
            b.putInt("openmode", OpenMode.UNKNOWN.ordinal)
        } else {
            b.putString("lastpath", tab.getOriginalPath(savePaths, requireMainActivity().prefs))
        }

        b.putString("home", tab.home)
        b.putInt("no", tab.tabNumber)
        // specifies if the constructed MainFragment hides the FAB when it is shown
        b.putBoolean(MainFragment.BUNDLE_HIDE_FAB, hideFabInTab)
        main.arguments = b
        viewModel.addFragment(main)
        sectionsPagerAdapter.notifyDataSetChanged()
        viewPager.offscreenPageLimit = 4
    }

    val currentTabFragment: Fragment?
        get() = if (viewModel.getFragments().size == 2) viewModel.getFragment(viewPager.currentItem)
        else null

    fun getFragmentAtIndex(pos: Int): Fragment? {
        return if (viewModel.getFragments().size == 2 && pos < 2) viewModel.getFragment(pos)
        else null
    }

    // updating indicator color as per the current viewpager tab
    fun updateIndicator(index: Int) {
        if (index != 0 && index != 1) return

        val accentColor = requireMainActivity().accent

        circleDrawable1!!.setImageDrawable(ColorCircleDrawable(accentColor))
        circleDrawable2!!.setImageDrawable(ColorCircleDrawable(Color.GRAY))
    }

    fun updateBottomBar(mainFragment: MainFragment?) {
        if (mainFragment == null || mainFragment.mainFragmentViewModel == null) {
            LOG.warn("Failed to update bottom bar: main fragment not available")
            return
        }
        requireMainActivity()
            .appbar
            .bottomBar
            .updatePath(
                mainFragment.currentPath!!,
                mainFragment.mainFragmentViewModel!!.openMode,
                mainFragment.mainFragmentViewModel!!.folderCount,
                mainFragment.mainFragmentViewModel!!.fileCount,
                mainFragment
            )
    }

    fun initLeftRightAndTopDragListeners(destroy: Boolean, shouldInvokeLeftAndRight: Boolean) {
        if (shouldInvokeLeftAndRight) {
            initLeftAndRightDragListeners(destroy)
        }
        for (fragment in viewModel.getFragments()) {
            if (fragment is MainFragment) {
                fragment.initTopAndEmptyAreaDragListeners(destroy)
            }
        }
    }

    private fun initLeftAndRightDragListeners(destroy: Boolean) {
        val mainFragment = requireMainActivity().currentMainFragment
        val leftPlaceholder = viewBinding.placeholderDragLeft
        val rightPlaceholder = viewBinding.placeholderDragRight
        val dragToTrash = viewBinding.placeholderTrashBottom
        val dataUtils = DataUtils.getInstance()
        if (destroy) {
            leftPlaceholder.setOnDragListener(null)
            rightPlaceholder.setOnDragListener(null)
            dragToTrash.setOnDragListener(null)
            leftPlaceholder.visibility = View.GONE
            rightPlaceholder.visibility = View.GONE
            dragToTrash.hideFade(150)
        } else {
            leftPlaceholder.visibility = View.VISIBLE
            rightPlaceholder.visibility = View.VISIBLE
            dragToTrash.showFade(150)
            leftPlaceholder.setOnDragListener(
                TabFragmentSideDragListener {
                    if (viewPager.currentItem == 1) {
                        if (mainFragment != null) {
                            dataUtils.checkedItemsList = mainFragment.adapter.checkedItems
                            requireMainActivity().actionModeHelper.disableActionMode()
                        }
                        viewPager.setCurrentItem(0, true)
                    }
                })
            rightPlaceholder.setOnDragListener(
                TabFragmentSideDragListener {
                    if (viewPager.currentItem == 0) {
                        if (mainFragment != null) {
                            dataUtils.checkedItemsList = mainFragment.adapter.checkedItems
                            requireMainActivity().actionModeHelper.disableActionMode()
                        }
                        viewPager.setCurrentItem(1, true)
                    }
                })
            dragToTrash.setOnDragListener(
                DragToTrashListener(
                    {
                        if (mainFragment != null) {
                            GeneralDialogCreation.deleteFilesDialog(
                                requireContext(),
                                requireMainActivity(),
                                mainFragment.adapter.checkedItems,
                                requireMainActivity().appTheme
                            )
                        } else {
                            AppConfig.toast(
                                requireContext(),
                                getString(R.string.operation_unsuccesful)
                            )
                        }
                    },
                    {
                        dragToTrash.performHapticFeedback(
                            HapticFeedbackConstants.LONG_PRESS,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                        )
                    })
            )
        }
    }

    private fun requireMainActivity(): MainActivity {
        return requireActivity() as MainActivity
    }

    companion object {
        @JvmStatic
        private val LOG: Logger = LoggerFactory.getLogger(TabFragment::class.java)

        const val KEY_PATH: String = "path"
        private const val KEY_POSITION = "pos"

        private const val KEY_FRAGMENT_0 = "tab0"
        private const val KEY_FRAGMENT_1 = "tab1"
    }
}
