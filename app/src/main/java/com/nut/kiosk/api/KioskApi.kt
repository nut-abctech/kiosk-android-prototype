package com.nut.kiosk.api;

import com.nut.kiosk.model.Page
import io.reactivex.Observable
import io.reactivex.Single
import retrofit2.http.GET
import retrofit2.http.Query
import okhttp3.ResponseBody
import retrofit2.http.Path


interface KioskApi {
    companion object {
        const val BASE_URL = "https://static-simple.herokuapp.com/"
    }

    @GET("pages")
    fun getPageList(
            //      @Query(RemoteContract.ACCESS_KEY) accessKey: String,
            //      @Query(RemoteContract.CURRENCIES) currencies: String,
            //      @Query(RemoteContract.FORMAT) format: String
    ): Single<List<Page>>

    @GET("{path}")
    fun getPage(
            @Path("path") path: String
    ): Single<ResponseBody>
}
