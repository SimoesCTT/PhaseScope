package com.simoesctt.phasescope

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var txtFile: TextView
    private lateinit var txtOrder: TextView
    private lateinit var txtOrderLabel: TextView
    private lateinit var txtChannels: TextView
    private lateinit var spinnerBand: Spinner
    private lateinit var btnAnalyze: Button
    private lateinit var btnExport: Button
    private lateinit var plotView: PlotView

    private var loadedEeg: EegLoader.Eeg? = null
    private var lastResult: CoherenceEngine.Result? = null
    private var lastBand: String = ""

    private val bands = listOf(
        "Delta (0.5–4 Hz)",
        "Theta (4–8 Hz)",
        "Alpha (8–12 Hz)",
        "Beta (12–30 Hz)",
        "Gamma (30–80 Hz)"
    )
    private val bandRanges = listOf(
        0.5 to 4.0,
        4.0 to 8.0,
        8.0 to 12.0,
        12.0 to 30.0,
        30.0 to 80.0
    )

    private val pickCsv = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) loadCsv(uri)
    }

    private val pickEdf = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) loadEdf(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtFile = findViewById(R.id.txtFile)
        txtOrder = findViewById(R.id.txtOrder)
        txtOrderLabel = findViewById(R.id.txtOrderLabel)
        txtChannels = findViewById(R.id.txtChannels)
        spinnerBand = findViewById(R.id.spinnerBand)
        btnAnalyze = findViewById(R.id.btnAnalyze)
        btnExport = findViewById(R.id.btnExport)
        plotView = findViewById(R.id.plotView)

        spinnerBand.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, bands
        )

        findViewById<Button>(R.id.btnLoad).setOnClickListener {
            pickCsv.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "*/*"))
        }
        findViewById<Button>(R.id.btnLoadEdf).setOnClickListener {
            pickEdf.launch(arrayOf("*/*"))
        }
        btnAnalyze.setOnClickListener { analyze() }
        btnExport.isEnabled = false
        btnExport.setOnClickListener { exportResults() }
    }

    private fun loadCsv(uri: Uri) {
        btnAnalyze.isEnabled = false
        txtFile.text = "Loading CSV…"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val eeg = EegLoader.loadCsv(this@MainActivity, uri)
                bind(eeg)
            } catch (e: Exception) {
                showError("CSV load failed: ${e.message}")
            }
        }
    }

    private fun loadEdf(uri: Uri) {
        btnAnalyze.isEnabled = false
        txtFile.text = "Loading EDF…"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val edf = EdfLoader.load(this@MainActivity, uri)
                // Convert Edf to Eeg-compatible structure
                val eeg = EegLoader.Eeg(edf.channels, edf.sampleRate, edf.channelNames)
                bind(eeg)
            } catch (e: Exception) {
                showError("EDF load failed: ${e.message}")
            }
        }
    }

    private suspend fun bind(eeg: EegLoader.Eeg) {
        withContext(Dispatchers.Main) {
            loadedEeg = eeg
            txtFile.text = "${eeg.channelNames.size} channels, " +
                    "${eeg.channels[0].size} samples, " +
                    "~${eeg.sampleRate.toInt()} Hz"
            txtChannels.text = eeg.channelNames.joinToString(", ")
            btnAnalyze.isEnabled = true
            btnExport.isEnabled = false
            plotView.matrix = null
            plotView.rTimeline = null
            txtOrder.text = "—"
            txtOrderLabel.text = ""
            lastResult = null
        }
    }

    private fun showError(msg: String) {
        CoroutineScope(Dispatchers.Main).launch {
            txtFile.text = msg
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun analyze() {
        val eeg = loadedEeg ?: return
        val bandIdx = spinnerBand.selectedItemPosition
        val (lo, hi) = bandRanges[bandIdx]
        val bandName = bands[bandIdx]

        btnAnalyze.isEnabled = false
        btnAnalyze.text = "Analyzing…"

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val result = CoherenceEngine.analyze(eeg.channels, eeg.sampleRate, lo, hi)
                withContext(Dispatchers.Main) {
                    lastResult = result
                    lastBand = bandName
                    plotView.matrix = result.matrix
                    plotView.rTimeline = result.rTimeline
                    plotView.bandName = bandName

                    txtOrder.text = String.format("%.3f", result.meanR)
                    txtOrderLabel.text = when {
                        result.meanR > 0.7 -> "Strong synchrony"
                        result.meanR > 0.4 -> "Moderate synchrony"
                        result.meanR > 0.2 -> "Weak synchrony"
                        else -> "No synchrony"
                    }
                    btnAnalyze.isEnabled = true
                    btnAnalyze.text = "Analyze"
                    btnExport.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnAnalyze.isEnabled = true
                    btnAnalyze.text = "Analyze"
                    Toast.makeText(this@MainActivity, "Analysis failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportResults() {
        val eeg = loadedEeg ?: return
        val result = lastResult ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = Exporter.exportCsv(
                    this@MainActivity,
                    result.matrix,
                    result.rTimeline,
                    lastBand,
                    eeg.channelNames
                )
                withContext(Dispatchers.Main) {
                    Exporter.share(this@MainActivity, file, "text/csv")
                }
            } catch (e: Exception) {
                showError("Export failed: ${e.message}")
            }
        }
    }
}
