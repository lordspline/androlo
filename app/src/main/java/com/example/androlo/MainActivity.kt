package com.example.androlo

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.example.androlo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun buttonStartDetectionClicked(view: View) {
        val intent = Intent(this, Detector::class.java)
        startActivity(intent)
    }
//    /**
//     * A native method that is implemented by the 'androlo' native library,
//     * which is packaged with this application.
//     */
//    external fun stringFromJNI(): String
//
    companion object {
        // Used to load the 'androlo' library on application startup.
        init {
            System.loadLibrary("androlo")
        }
    }
}