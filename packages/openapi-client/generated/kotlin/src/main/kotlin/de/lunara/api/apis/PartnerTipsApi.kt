package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import de.lunara.api.models.CreateUserDefinedTipDto
import de.lunara.api.models.TipFeedbackDto
import de.lunara.api.models.TodayTipResponse
import de.lunara.api.models.UpdateUserDefinedTipDto
import de.lunara.api.models.UserDefinedTipResponseDto

interface PartnerTipsApi {
    /**
     * POST api/v1/tips/{id}/feedback
     * 
     * 
     * Responses:
     *  - 201: Successful response
     *
     * @param id 
     * @param tipFeedbackDto 
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @POST("api/v1/tips/{id}/feedback")
    suspend fun tipsFeedback(@Path("id") id: kotlin.String, @Body tipFeedbackDto: TipFeedbackDto): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

    /**
     * GET api/v1/tips/today
     * 
     * 
     * Responses:
     *  - 200: Successful response
     *
     * @return [TodayTipResponse]
     */
    @GET("api/v1/tips/today")
    suspend fun tipsToday(): Response<TodayTipResponse>

    /**
     * POST api/v1/tips/custom
     * 
     * 
     * Responses:
     *  - 201: 
     *
     * @param createUserDefinedTipDto 
     * @return [UserDefinedTipResponseDto]
     */
    @POST("api/v1/tips/custom")
    suspend fun trackerTipsCreate(@Body createUserDefinedTipDto: CreateUserDefinedTipDto): Response<UserDefinedTipResponseDto>

    /**
     * DELETE api/v1/tips/custom/{id}
     * 
     * 
     * Responses:
     *  - 204: User-defined tip disabled
     *
     * @param id 
     * @return [Unit]
     */
    @DELETE("api/v1/tips/custom/{id}")
    suspend fun trackerTipsDisable(@Path("id") id: kotlin.String): Response<Unit>

    /**
     * GET api/v1/tips/custom
     * 
     * 
     * Responses:
     *  - 200: 
     *
     * @param includeDisabled  (optional)
     * @return [kotlin.collections.List<UserDefinedTipResponseDto>]
     */
    @GET("api/v1/tips/custom")
    suspend fun trackerTipsList(@Query("includeDisabled") includeDisabled: kotlin.Boolean? = null): Response<kotlin.collections.List<UserDefinedTipResponseDto>>

    /**
     * PATCH api/v1/tips/custom/{id}
     * 
     * 
     * Responses:
     *  - 200: 
     *
     * @param id 
     * @param updateUserDefinedTipDto 
     * @return [UserDefinedTipResponseDto]
     */
    @PATCH("api/v1/tips/custom/{id}")
    suspend fun trackerTipsUpdate(@Path("id") id: kotlin.String, @Body updateUserDefinedTipDto: UpdateUserDefinedTipDto): Response<UserDefinedTipResponseDto>

}
