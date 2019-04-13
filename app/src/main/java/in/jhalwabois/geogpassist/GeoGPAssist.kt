package `in`.jhalwabois.geogpassist

import android.app.Application
import com.androidnetworking.AndroidNetworking




class GeoGPAssist : Application() {

    override fun onCreate() {
        super.onCreate()
        AndroidNetworking.initialize(applicationContext);
    }
}