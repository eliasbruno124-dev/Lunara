package de.lunara.api.apis

import de.lunara.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import com.squareup.moshi.Json

import de.lunara.api.models.AuthResponse
import de.lunara.api.models.ChangePasswordDto
import de.lunara.api.models.LoginDto
import de.lunara.api.models.LogoutDto
import de.lunara.api.models.OidcLinkDto
import de.lunara.api.models.OidcLoginDto
import de.lunara.api.models.RefreshDto
import de.lunara.api.models.RegisterDto
import de.lunara.api.models.SessionResponse
import de.lunara.api.models.SetupDto

interface AuthenticationApi {
    /**
     * POST api/v1/auth/change-password
     *
     *
     * Responses:
     *  - 204:
     *
     * @param changePasswordDto
     * @return [Unit]
     */
    @POST("api/v1/auth/change-password")
    suspend fun authChangePassword(@Body changePasswordDto: ChangePasswordDto): Response<Unit>

    /**
     * POST api/v1/auth/login
     *
     *
     * Responses:
     *  - 201: Successful response
     *
     * @param loginDto
     * @return [AuthResponse]
     */
    @POST("api/v1/auth/login")
    suspend fun authLogin(@Body loginDto: LoginDto): Response<AuthResponse>

    /**
     * POST api/v1/auth/logout
     *
     *
     * Responses:
     *  - 204:
     *
     * @param logoutDto
     * @return [Unit]
     */
    @POST("api/v1/auth/logout")
    suspend fun authLogout(@Body logoutDto: LogoutDto): Response<Unit>

    /**
     * POST api/v1/auth/refresh
     *
     *
     * Responses:
     *  - 201: Successful response
     *
     * @param refreshDto
     * @return [AuthResponse]
     */
    @POST("api/v1/auth/refresh")
    suspend fun authRefresh(@Body refreshDto: RefreshDto): Response<AuthResponse>

    /**
     * POST api/v1/auth/register
     * Create an isolated private relationship and its TRACKER account when registration is enabled
     *
     * Responses:
     *  - 201: Successful response
     *
     * @param registerDto
     * @return [AuthResponse]
     */
    @POST("api/v1/auth/register")
    suspend fun authRegister(@Body registerDto: RegisterDto): Response<AuthResponse>

    /**
     * DELETE api/v1/auth/sessions/{id}
     *
     *
     * Responses:
     *  - 204:
     *
     * @param id
     * @return [Unit]
     */
    @DELETE("api/v1/auth/sessions/{id}")
    suspend fun authRevokeSession(@Path("id") id: kotlin.String): Response<Unit>

    /**
     * GET api/v1/auth/sessions
     *
     *
     * Responses:
     *  - 200: Successful response
     *
     * @return [kotlin.collections.List<SessionResponse>]
     */
    @GET("api/v1/auth/sessions")
    suspend fun authSessions(): Response<kotlin.collections.List<SessionResponse>>

    /**
     * POST api/v1/setup
     * Create the first private relationship and TRACKER account
     *
     * Responses:
     *  - 201: Successful response
     *
     * @param setupDto
     * @return [AuthResponse]
     */
    @POST("api/v1/setup")
    suspend fun authSetup(@Body setupDto: SetupDto): Response<AuthResponse>

    /**
     * POST api/v1/auth/oidc/link
     * Link the signed-in Lunara account to the exact OIDC subject after password re-authentication
     *
     * Responses:
     *  - 201: Successful response
     *
     * @param oidcLinkDto
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @POST("api/v1/auth/oidc/link")
    suspend fun oidcLink(@Body oidcLinkDto: OidcLinkDto): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

    /**
     * POST api/v1/auth/oidc/login
     * Exchange a verified, explicitly linked OIDC identity for a Lunara session
     *
     * Responses:
     *  - 201: Successful response
     *
     * @param oidcLoginDto
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @POST("api/v1/auth/oidc/login")
    suspend fun oidcLogin(@Body oidcLoginDto: OidcLoginDto): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

    /**
     * GET api/v1/auth/oidc/status
     * Show external sign-in and local password status
     *
     * Responses:
     *  - 200: Successful response
     *
     * @return [kotlin.collections.Map<kotlin.String, kotlin.Any>]
     */
    @GET("api/v1/auth/oidc/status")
    suspend fun oidcStatus(): Response<kotlin.collections.Map<kotlin.String, kotlin.Any>>

}
