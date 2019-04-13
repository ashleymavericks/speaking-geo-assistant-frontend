package `in`.jhalwabois.geogpassist

import `in`.jhalwabois.geogpassist.adapters.CardData
import `in`.jhalwabois.geogpassist.adapters.CardListAdapter
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.support.design.widget.BottomSheetBehavior
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.data.geojson.*
import kotlinx.android.synthetic.main.assist_bottomsheet.*
import kotlinx.android.synthetic.main.content_maps.*
import kotlinx.android.synthetic.main.persistent_assistant_layout.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.*


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {


    private lateinit var mMap: GoogleMap
    private var textToSpeech: TextToSpeech? = null
    private val adapter = CardListAdapter()

    private var currentLanguage = "en-US"

    private var activeLayer: GeoJsonLayer? = null
    private var multiActiveLayer = mutableListOf<GeoJsonLayer>()

    private lateinit var bottomSheetBehaviour: BottomSheetBehavior<LinearLayout>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)

        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        bottomSheetBehaviour = BottomSheetBehavior.from(bottomSheet)
        clearLayerButton.setOnClickListener {
            activeLayer?.removeLayerFromMap()
            multiActiveLayer.forEach { it.removeLayerFromMap() }
            activeLayer = null
            multiActiveLayer.clear()
        }

        micButton.setOnClickListener {
            if (bottomSheetBehaviour.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehaviour.state = BottomSheetBehavior.STATE_COLLAPSED
            }
            promptSpeechInput()

        }

        compensateTTSDelay()

        bottomSheetBehaviour.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                assistInputCard.cardElevation =
                        if (slideOffset >= 0f) 0f else (-slideOffset) * 30f

                if (slideOffset == -1f) {
                    whiteOverlay.visibility = View.INVISIBLE
                } else whiteOverlay.visibility = View.VISIBLE
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        separatorBottomBar.visibility = View.INVISIBLE
                        assistInputCard.cardElevation = 30f
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        separatorBottomBar.visibility = View.VISIBLE
                        assistInputCard.cardElevation = 0f
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        separatorBottomBar.visibility = View.VISIBLE
                        assistInputCard.cardElevation = 0f
                    }
                }
            }

        })
        bottomSheetBehaviour.state = BottomSheetBehavior.STATE_HIDDEN

    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val sydney = LatLng(84.14467752000007, 18.571550984000055)
        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
        mMap.mapType = GoogleMap.MAP_TYPE_HYBRID;

        val data = JSONObject(getString(R.string.geojsonexample))
        addGeoGPLayer(data, false)
    }

    private fun addGeoGPLayer(geoJSONObject: JSONObject, show: Boolean, centroid: LatLng? = null) {
        val layer = GeoJsonLayer(mMap, geoJSONObject)

        if (show) {
            multiActiveLayer.add(layer)
            layer.addLayerToMap()
        }

        if (centroid == null) {
            layer.features.forEach {
                val p = it.geometry.geometryObject as ArrayList<*>
                // Polygon - LineString - Point
                val q = p[0] as GeoJsonPolygon
                val center = findCenter(q.coordinates.flatten())
                moveToPoint(center, 13)
            }
        } else {
            moveToPoint(centroid, 15)
        }

    }

    private fun findCenter(list: List<LatLng>): LatLng {
        val boundingBoxBuilder = LatLngBounds.Builder()
        list.forEach { boundingBoxBuilder.include(it) }
        val box = boundingBoxBuilder.build()
        return box.center
    }

    private fun moveToPoint(latLng: LatLng, zoomLevel: Int) {
        val move = CameraUpdateFactory.newLatLngZoom(latLng, zoomLevel.toFloat())
        mMap.moveCamera(move)
    }


    private fun promptSpeechInput() {

        userTextView.visibility = View.VISIBLE
        userTextView.text = "Listening ..."

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                "com.domain.app")
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

        val recognizer = SpeechRecognizer
                .createSpeechRecognizer(this.applicationContext)
        val listener = object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val voiceResults = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (voiceResults == null) {
                    userTextView.visibility = View.GONE
                    Log.e("fragment", "No voice results")
                } else {
                    Log.d("fragment", "Printing matches: ")
                    for (match in voiceResults) {
                        Log.d("fragment", match)
                    }

                    userTextView.text = voiceResults[0]
                    requestForQuery(voiceResults[0])
                    //Toast.makeText(this@MapsActivity, voiceResults[0], Toast.LENGTH_SHORT).show()
                }

                recognizer.destroy()
            }

            override fun onReadyForSpeech(params: Bundle) {
                Log.d("fragment", "Ready for speech")
            }

            override fun onError(error: Int) {
                Log.d("fragment",
                        "Error listening for speech: " + error)
                Toast.makeText(this@MapsActivity, "Could not recognize speech, try again.", Toast.LENGTH_SHORT).show()

                recognizer.destroy()
            }

            override fun onBeginningOfSpeech() {
                Log.d("fragment", "Speech starting")
            }

            override fun onBufferReceived(buffer: ByteArray) {
                // This method is intentionally empty
            }

            override fun onEndOfSpeech() {
                /*if (speechprogress != null)
                    speechprogress.onEndOfSpeech()*/
            }

            override fun onEvent(eventType: Int, params: Bundle) {
                // This method is intentionally empty
            }

            override fun onPartialResults(partialResults: Bundle) {
                val partial = partialResults
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                userTextView.text = partial[0]
                //txtchat?.text = partial[0]
            }

            override fun onRmsChanged(rmsdB: Float) {
                /*if (speechprogress != null)
                    speechprogress.onRmsChanged(rmsdB)*/
            }
        }
        recognizer.setRecognitionListener(listener)
        recognizer.startListening(intent)
    }

    private fun showGeoJSON(data: JSONObject) {

        val type = data.getJSONObject("location").getString("type")
        val location = data.getJSONObject("centroid")
        val coordinates = location.getJSONArray("coordinates")

        if (coordinates.get(0) == null) {
            return
        }
        val latLng = LatLng(coordinates.getDouble(1), coordinates.getDouble(0))

        when (type) {
            "Point" -> {
                activeLayer?.removeLayerFromMap()
                activeLayer = GeoJsonLayer(mMap, data.getJSONObject("location"))
                activeLayer?.addLayerToMap()

                activeLayer?.features?.forEach {

                    val q = it.geometry.geometryObject as LatLng

                    val zoom = CameraUpdateFactory.zoomTo(14f)

                    val q2 = LatLng(q.latitude - 0.003, q.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(q2))
                    mMap.animateCamera(zoom)
                }
            }
            "Polygon" -> {
                activeLayer?.removeLayerFromMap()
                activeLayer = GeoJsonLayer(mMap, data.getJSONObject("location"))

                activeLayer?.features?.forEach {
                    it.polygonStyle = GeoJsonPolygonStyle().apply {
                        fillColor = Color.parseColor("#88dee8e7")
                        strokeColor = Color.parseColor("#22a4f5")
                    }
                }

                activeLayer?.addLayerToMap()
                moveToPoint(latLng, 15)

            }
            "LineString" -> {
                activeLayer?.removeLayerFromMap()
                activeLayer = GeoJsonLayer(mMap, data.getJSONObject("location"))

                activeLayer?.features?.forEach {
                    it.lineStringStyle = GeoJsonLineStringStyle().apply {
                        color = Color.parseColor("#22a4f5")
                    }
                }

                activeLayer?.addLayerToMap()
                moveToPoint(latLng, 15)
            }
        }
    }

    private fun addObjectToMap(layer: GeoJsonLayer, addNew: Boolean = false) {
        if (addNew) {
            activeLayer?.removeLayerFromMap()
            activeLayer = layer
            activeLayer?.addLayerToMap()
        } else {
            multiActiveLayer.add(layer)
            layer.addLayerToMap()
        }
    }

    private fun requestForQuery(s: String?) {
        s?.apply {

            val req = getResponseForQuery(this, currentLanguage)
            req.getAsJSONObject(object : JSONObjectRequestListener {

                override fun onResponse(response: JSONObject) {
                    parseIntent(response)
                }

                override fun onError(anError: ANError?) {
                    anError?.printStackTrace()
                }

            })
        }
    }

    private fun voiceReply(reply: String, language: String) {
        val audioFocus = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val handler = Handler()
        val afChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                textToSpeech?.stop()
            }
        }
        handler.post {
            val result = audioFocus.requestAudioFocus(afChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                val ttsParams = HashMap<String, String>()
                ttsParams[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = this@MapsActivity.packageName
                textToSpeech?.language = Locale(language)
                textToSpeech?.speak(reply, TextToSpeech.QUEUE_FLUSH, ttsParams)
                audioFocus.abandonAudioFocus(afChangeListener)
            }
        }

    }

    private fun compensateTTSDelay() {
        Handler().post {
            textToSpeech = TextToSpeech(applicationContext, TextToSpeech.OnInitListener { status ->
                if (status != TextToSpeech.ERROR) {
                    val locale = Locale(currentLanguage)
                    textToSpeech?.language = locale
                }
            })
        }
    }

    private fun changeLanguage(language: String = "en") {
        this.currentLanguage = language
        compensateTTSDelay()
    }

    private fun parseIntent(data: JSONObject) {

        val message = data.getString("message")
        voiceReply(message, currentLanguage)

        assistTextView.visibility = View.VISIBLE
        bottomSheetBehaviour.state = BottomSheetBehavior.STATE_EXPANDED
        assistTextView.text = message
        assistHelpTextView.visibility = View.GONE

        try {

            val intent = data.getJSONObject("intent")
            val action = intent.getString("action")

            when (action) {

                "changeLanguage" -> {
                    val language = intent.getString("lang")
                    changeLanguage(language)

                    if (language == "be")
                        changeLanguage("bn")

                }
                "plot" -> {
                    val geoInfo = data.getJSONArray("geoInfo")
                    val list = mutableListOf<CardData>()

                    geoInfo.getObjects().forEach {
                        val type = it.getJSONObject("location").getString("type")
                        val showInCards = it.getBoolean("showInCards")

                        if (!showInCards) {
                            val layer = GeoJsonLayer(mMap, it.getJSONObject("location"))
                            addObjectToMap(layer, addNew = false)
                        }

                        if (showInCards) {
                            when (type) {
                                "Point" -> {

                                    val location = it.getJSONObject("location")
                                    val coordinates = location.getJSONArray("coordinates")

                                    val area = it.getJSONObject("metadata")
                                            .getDouble("Shape_Area")

                                    list.add(CardData(
                                            lat = coordinates.getDouble(0),
                                            long = coordinates.getDouble(1),
                                            area = area
                                    ))
                                }
                                "Polygon" -> {
                                    val d = it
                                    val location = d.getJSONObject("centroid")
                                    val coordinates = location.getJSONArray("coordinates")

                                    val area = d.getJSONObject("metadata")
                                            .getDouble("Shape_Area")

                                    list.add(CardData(
                                            lat = coordinates.getDouble(0),
                                            long = coordinates.getDouble(1),
                                            area = area
                                    ))
                                }
                                "LineString" -> {
                                    val d = it
                                    val location = d.getJSONObject("centroid")
                                    val coordinates = location.getJSONArray("coordinates")

                                    val area = d.getJSONObject("metadata")
                                            .getDouble("Shape_Area")

                                    list.add(CardData(
                                            lat = coordinates.getDouble(0),
                                            long = coordinates.getDouble(1),
                                            area = area
                                    ))
                                }
                                "MultiLineString" -> {
                                    val d = it
                                    val location = d.getJSONObject("centroid")
                                    val coordinates = location.getJSONArray("coordinates")

                                    val area = d.getJSONObject("metadata")
                                            .getDouble("Shape_Area")

                                    list.add(CardData(
                                            lat = coordinates.getDouble(0),
                                            long = coordinates.getDouble(1),
                                            area = area
                                    ))
                                }
                            }
                        }

                        recyclerView.visibility = View.VISIBLE
                        Log.d("LIST", list.toString())
                        if (list.size > 0) {
                            adapter.updateLocations(list)
                            showGeoJSON(geoInfo.getJSONObject(0))
                            adapter.onItemSelected = {
                                showGeoJSON(geoInfo.getJSONObject(it))
                            }
                        }
                    }


                }
                "moveTo" -> {
                    val loc = intent.getJSONObject("loc")
                    val lat = loc.getDouble("lat")
                    val lng = loc.getDouble("lng")

                    val latlng = LatLng(lat, lng)
                    val zoom = intent.getDouble("zoom")

                    moveToPoint(latlng, zoom.toInt())
                }
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
        }

    }

    fun JSONArray.getObjects(): List<JSONObject> {
        val mutableList = mutableListOf<JSONObject>()
        for (i in 0 until this.length()) {
            mutableList.add(this.getJSONObject(i))
        }
        return mutableList
    }

    fun JSONArray.getArray(): List<JSONArray> {
        val mutableList = mutableListOf<JSONArray>()
        for (i in 0 until this.length()) {
            mutableList.add(this.getJSONArray(i))
        }
        return mutableList
    }
}

