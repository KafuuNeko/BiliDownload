package cc.kafuu.bilidownload

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import cc.kafuu.bilidownload.common.jniexport.FFMpegJNI
import cc.kafuu.bilidownload.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.sampleText.text = FFMpegJNI.ffmpegInfo()

//        FFMpegJNI.videoFormatConversion("${getExternalFilesDir("tests")}/test.mp4", "${getExternalFilesDir("tests")}/test.flv")
//        Log.d(TAG, "onCreate: ")

    }
}