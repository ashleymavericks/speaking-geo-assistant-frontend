package `in`.jhalwabois.geogpassist

import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority



fun getResponseForQuery(query: String, currentLanguage: String) =
        /*AndroidNetworking.get("http://aksh-assistant.herokuapp.com/nlp")
                .addQueryParameter("q", query)
                .setPriority(Priority.HIGH)
                .build()*/
        AndroidNetworking.get("http://10.215.99.159:3000/nlp")
                .addQueryParameter("q", query)
                .addQueryParameter("lang", currentLanguage)
                .setPriority(Priority.HIGH)
                .build()
