package cc.kafuu.bilidownload.feature.viewbinding.view.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.utils.BiliAddressParser

/**
 * Narrow exported entry point for text shared by other applications.
 */
class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent.takeIf {
            it.action == Intent.ACTION_SEND && it.type == MIME_TYPE_TEXT
        }?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val address = BiliAddressParser.extractSupportedAddress(sharedText)

        if (address == null) {
            Toast.makeText(this, R.string.share_import_unsupported_message, Toast.LENGTH_SHORT)
                .show()
        } else {
            startActivity(
                SearchActivity.buildIntent(address)
                    .setClass(this, SearchActivity::class.java)
            )
        }
        finish()
    }

    private companion object {
        const val MIME_TYPE_TEXT = "text/plain"
    }
}
