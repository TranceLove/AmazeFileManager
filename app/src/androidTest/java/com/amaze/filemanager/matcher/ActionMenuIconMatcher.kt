package com.amaze.filemanager.matcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import androidx.annotation.DrawableRes
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.test.espresso.matcher.BoundedMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher

/**
 * This matcher allows to select by [ActionMenuItemView] icon
 *
 * From https://stackoverflow.com/a/70108466/3124150
 */
object ActionMenuIconMatcher {
    /**
     * A [Matcher] that will match against an [ActionMenuItemView] view with a specific icon
     * resource
     */
    @JvmStatic
    fun withActionIconDrawable(
        @DrawableRes resourceId: Int,
    ): Matcher<View?> {
        return object : BoundedMatcher<View?, ActionMenuItemView>(ActionMenuItemView::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("has image drawable resource $resourceId")
            }

            override fun matchesSafely(actionMenuItemView: ActionMenuItemView): Boolean {
                val iconDrawable = actionMenuItemView.itemData.icon ?: return false

                return sameBitmap(
                    actionMenuItemView.context,
                    iconDrawable,
                    resourceId,
                    actionMenuItemView,
                )
            }
        }
    }

    /**
     * Compares a [Drawable] against a resource id, returns if they are identical
     */
    @JvmStatic
    private fun sameBitmap(
        context: Context,
        drawable: Drawable,
        @DrawableRes resourceId: Int,
        view: View,
    ): Boolean {
        var drawable = drawable
        val otherDrawable: Drawable = context.resources.getDrawable(resourceId) ?: return false

        if (drawable is StateListDrawable) {
            val getStateDrawableIndex =
                StateListDrawable::class.java.getMethod(
                    "getStateDrawableIndex",
                    IntArray::class.java,
                )
            val getStateDrawable =
                StateListDrawable::class.java.getMethod(
                    "getStateDrawable",
                    Int::class.javaPrimitiveType,
                )
            val index = getStateDrawableIndex.invoke(drawable, view.drawableState)
            drawable = getStateDrawable.invoke(drawable, index) as Drawable
        }

        val bitmap = getBitmapFromDrawable(drawable)
        val otherBitmap = getBitmapFromDrawable(otherDrawable)
        return bitmap.sameAs(otherBitmap)
    }

    /**
     * Convert a [Bitmap] to a [Drawable] by drawing to a canvas
     */
    @JvmStatic
    private fun getBitmapFromDrawable(drawable: Drawable): Bitmap {
        val bitmap: Bitmap =
            Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
