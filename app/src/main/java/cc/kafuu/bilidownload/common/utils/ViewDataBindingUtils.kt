@file:Suppress("NAME_SHADOWING")

package cc.kafuu.bilidownload.common.utils

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.text.Html
import android.text.Spanned
import android.text.TextUtils
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreRVAdapter
import cc.kafuu.bilidownload.common.model.bili.BiliAccountModel
import com.bumptech.glide.Glide
import com.bumptech.glide.load.Transformation
import com.bumptech.glide.load.resource.bitmap.FitCenter

@BindingAdapter(value = ["bindDataList"])
fun bindDataList(recyclerView: RecyclerView, data: List<Any>?) {
    (recyclerView.adapter as? CoreRVAdapter<*>)?.setDataList(data)
}

@BindingAdapter(
    value = ["bindImageUrl", "bindPlaceholder", "bindTransformation", "defaultDrawable"],
    requireAll = false
)
fun bindGlideData(
    imageView: ImageView,
    bindImageUrl: String?,
    bindPlaceholder: Drawable?,
    bindTransformation: Transformation<Bitmap?>?,
    defaultDrawable: Drawable?
) {
    if (bindImageUrl == null || TextUtils.isEmpty(bindImageUrl)) {
        doLoadDrawable(
            defaultDrawable ?: CommonLibs.getDrawable(R.drawable.ic_2233),
            imageView, bindPlaceholder, bindTransformation
        )
    } else {
        doLoadImageUrl(bindImageUrl, imageView, bindPlaceholder, bindTransformation)
    }
}

@BindingAdapter(
    value = ["bindProfileAccount", "bindPlaceholder", "bindTransformation", "defaultDrawable"],
    requireAll = false
)
fun bindGlideData(
    imageView: ImageView,
    bindProfileAccount: BiliAccountModel?,
    bindPlaceholder: Drawable?,
    bindTransformation: Transformation<Bitmap?>?,
    defaultDrawable: Drawable?
) {
    bindGlideData(
        imageView,
        bindProfileAccount?.profile,
        bindPlaceholder,
        bindTransformation,
        defaultDrawable
    )
}

@SuppressLint("CheckResult")
private fun doLoadImageUrl(
    url: String,
    imageView: ImageView,
    bindPlaceholder: Drawable?,
    bindTransformation: Transformation<Bitmap?>?
) {
    val glide = Glide.with(CommonLibs.requireContext()).apply {
        clear(imageView)
        if (url.endsWith(".gif")) {
            asGif()
        }
    }
    glide.load(url).placeholder(bindPlaceholder)
        .optionalTransform(bindTransformation ?: FitCenter())
        .into(imageView)
}

@SuppressLint("CheckResult")
private fun doLoadDrawable(
    drawable: Drawable?,
    imageView: ImageView,
    bindPlaceholder: Drawable?,
    bindTransformation: Transformation<Bitmap?>?
) {
    Glide.with(CommonLibs.requireContext()).load(drawable).placeholder(bindPlaceholder)
        .optionalTransform(bindTransformation ?: FitCenter())
        .into(imageView)
}

@BindingAdapter(value = ["bindVisible"])
fun bindVisible(view: View, visibility: Boolean) {
    view.visibility = if (visibility) View.VISIBLE else View.GONE
}

@BindingAdapter(value = ["bindOnEditorAction"])
fun bindOnEditorAction(textView: TextView, onEditorActionLambda: () -> Unit) {
    textView.setOnEditorActionListener { _, _, _ ->
        onEditorActionLambda()
        true
    }
}

@BindingAdapter("bindSpannable")
fun bindSpannable(textView: TextView, htmlText: String?) {
    if (htmlText == null) {
        textView.text = ""
    } else {
        val spannedText: Spanned =
            Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY)
        textView.text = spannedText
    }
}

@BindingAdapter("bindSpinnerEntries")
fun bindSpinnerEntries(spinner: Spinner, entries: Array<String>) {
    spinner.apply {
        adapter = ArrayAdapter(spinner.context, R.layout.spinner_item, entries)
    }
}