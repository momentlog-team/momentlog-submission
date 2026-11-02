package team.momentlog.app.core.data.api

import team.momentlog.app.core.data.model.record.DocumentRequest
import team.momentlog.app.core.data.model.record.DocumentResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import team.momentlog.app.core.data.model.record.SealRequest
import team.momentlog.app.core.data.model.record.SealResponse
import team.momentlog.app.core.data.model.record.VerifyRequest
import team.momentlog.app.core.data.model.record.VerifyResponse

/** 서버 `server/.../record/api/RecordSealController`와 맞춘다. */
interface RecordApi {

    @POST("api/v1/records/{recordId}/seal")
    suspend fun seal(@Path("recordId") recordId: String, @Body request: SealRequest): SealResponse

    @POST("api/v1/records/{recordId}/verify")
    suspend fun verify(@Path("recordId") recordId: String, @Body request: VerifyRequest): VerifyResponse

    @POST("api/v1/records/{recordId}/documents")
    suspend fun createDocument(@Path("recordId") recordId: String, @Body request: DocumentRequest): DocumentResponse
}
