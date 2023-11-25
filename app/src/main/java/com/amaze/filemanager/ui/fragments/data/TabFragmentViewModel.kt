package com.amaze.filemanager.ui.fragments.data

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel

class TabFragmentViewModel: ViewModel() {

    private val fragments: MutableList<Fragment> = ArrayList()

    fun getFragments(): List<Fragment> = fragments

    fun clearFragmentList() = fragments.clear()

    fun addFragment(index: Int, fragment: Fragment) = fragments.add(index, fragment)

    fun addFragment(fragment: Fragment) = fragments.add(fragment)

    fun getFragment(index: Int): Fragment = fragments.get(index)
}