package edu.temple.convoy
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ConvoyApi {
    @FormUrlEncoded
    @POST("account.php")
    suspend fun account(
        @Field("action") action: String,
        @Field("username") username: String,
        @Field("password") password: String?,
        @Field("fcm_token") fcmToken: String?,
        @Field("firstname") firstname: String?,
        @Field("lastname") lastname: String?,
        @Field("session_key") sessionKey: String?
    ): Map<String, Any>
    @FormUrlEncoded
    @POST("convoy.php")
    suspend fun convoy(
        @Field("action") action: String,
        @Field("username") username: String,
        @Field("session_key") sessionKey: String,
        @Field("convoy_id") convoyId: String? = null,
        @Field("latitude") latitude: Double? = null,
        @Field("longitude") longitude: Double? = null
    ): Map<String, Any>
    @Multipart
    @POST("convoy.php")
    suspend fun convoyMessage(
        @Part("action") action: RequestBody,
        @Part("username") username: RequestBody,
        @Part("session_key") sessionKey: RequestBody,
        @Part("convoy_id") convoyId: RequestBody,
        @Part message_file: MultipartBody.Part
    ): Map<String, Any>
}