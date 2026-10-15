package com.overdrive.player.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class LibraryPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> SongsFragment()
        1 -> AlbumsFragment()
        2 -> ArtistsFragment()
        else -> FavoritesFragment()
    }
}
