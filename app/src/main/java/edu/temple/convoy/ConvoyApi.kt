package edu.temple.convoy
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface ConvoyApi {
    @FormUrlEncoded
    @POST("account.php")
    suspend fun account(
        @Field("action") action: String,
        @Field("username") username: String,
        @Field("password") password: String?,
        @Field("firstname") firstname: String?,
        @Field("lastname") lastname: String?,
        @Field("session_key") sessionKey: String?
    ): Map<String, Any>

}