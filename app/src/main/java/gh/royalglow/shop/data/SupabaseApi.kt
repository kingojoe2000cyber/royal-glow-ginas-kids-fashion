package gh.royalglow.shop.data

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Headers
import retrofit2.http.Query
import okhttp3.RequestBody
import okhttp3.ResponseBody
import java.util.concurrent.TimeUnit

/**
 * The publishable key is designed for use in client applications. Database access is
 * still governed by Row Level Security. Never place a Supabase secret/service-role key here.
 */
object SupabaseConfig {
    const val BASE_URL = "https://kgkcgfwabglhmsqfplry.supabase.co/"
    const val PUBLISHABLE_KEY = "sb_publishable_433KrflYeNKdXmsspxvMrg_eP5hgXvF"
}

object SupabaseApiFactory {
    val api: SupabaseApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                    // Per-user requests override this header with the signed-in access token.
                    .header("Authorization", chain.request().header("Authorization")
                        ?: "Bearer ${SupabaseConfig.PUBLISHABLE_KEY}")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()

        Retrofit.Builder()
            .baseUrl(SupabaseConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SupabaseApi::class.java)
    }
}

interface SupabaseApi {
    @GET("rest/v1/brands")
    suspend fun getBrands(
        @Query("select") select: String = "id,name,slug,description,logo_url,is_active",
        @Query("is_active") active: String = "eq.true"
    ): List<ShopBrandDto>

    @GET("rest/v1/categories")
    suspend fun getCategories(
        @Query("select") select: String = "id,brand_id,name,slug,description,display_order",
        @Query("is_active") active: String = "eq.true",
        @Query("brand_id") brandId: String? = null,
        @Query("order") order: String = "display_order.asc,name.asc"
    ): List<ShopCategoryDto>

    @GET("rest/v1/products")
    suspend fun getProducts(
        @Query("select") select: String =
            "id,brand_id,category_id,sku,name,slug,description,condition,age_min_months,age_max_months,size_label,colour,price,image_url,is_active,is_featured,brands(name),categories(name)",
        @Query("is_active") active: String = "eq.true",
        @Query("brand_id") brandId: String? = null,
        @Query("order") order: String = "is_featured.desc,created_at.desc"
    ): List<ShopProductDto>

    @POST("auth/v1/signup")
    suspend fun signUp(@Body request: SignUpRequest): AuthResponseDto

    @POST("auth/v1/token")
    suspend fun signIn(
        @Query("grant_type") grantType: String = "password",
        @Body request: SignInRequest
    ): AuthResponseDto

    @POST("auth/v1/logout")
    suspend fun signOut(@Header("Authorization") authorization: String)

    @GET("rest/v1/staff_members")
    suspend fun getMyStaffRole(
        @Query("select") select: String = "role,is_active",
        @Query("user_id") userId: String,
        @Header("Authorization") authorization: String
    ): List<StaffRoleDto>

    @GET("rest/v1/products")
    suspend fun getManagedProducts(
        @Query("select") select: String =
            "id,brand_id,category_id,sku,name,slug,description,condition,age_min_months,age_max_months,size_label,colour,price,image_url,is_active,is_featured,brands(name),categories(name)",
        @Query("order") order: String = "created_at.desc",
        @Header("Authorization") authorization: String
    ): List<ShopProductDto>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/products")
    suspend fun createProduct(
        @Body product: ProductWriteDto,
        @Header("Authorization") authorization: String
    ): List<ShopProductDto>

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/products")
    suspend fun updateProduct(
        @Query("id") productId: String,
        @Body product: ProductWriteDto,
        @Header("Authorization") authorization: String
    ): ResponseBody

    @Headers("Prefer: resolution=merge-duplicates,return=minimal")
    @POST("rest/v1/inventory")
    suspend fun upsertInventory(
        @Query("on_conflict") onConflict: String = "product_id",
        @Body inventory: InventoryWriteDto,
        @Header("Authorization") authorization: String
    ): ResponseBody

    @PUT("storage/v1/object/product-images/{objectPath}")
    suspend fun uploadProductImage(
        @Path(value = "objectPath", encoded = true) objectPath: String,
        @Body imageBytes: RequestBody,
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String,
        @Header("x-upsert") upsert: String = "false"
    ): ResponseBody

    @POST("rest/v1/rpc/place_customer_order")
    suspend fun placeCustomerOrder(
        @Body request: PlaceCustomerOrderRequest,
        @Header("Authorization") authorization: String
    ): JsonObject

    @GET("rest/v1/orders")
    suspend fun getMyOrders(
        @Query("select") select: String =
            "id,order_number,brand_id,customer_name,subtotal,delivery_fee,total_amount,order_status,payment_status,created_at",
        @Query("order") order: String = "created_at.desc",
        @Header("Authorization") authorization: String
    ): List<CustomerOrderDto>
}

data class ShopBrandDto(
    val id: String,
    val name: String,
    val slug: String,
    val description: String?,
    @SerializedName("logo_url") val logoUrl: String?,
    @SerializedName("is_active") val isActive: Boolean
)

data class ShopCategoryDto(
    val id: String,
    @SerializedName("brand_id") val brandId: String,
    val name: String,
    val slug: String,
    val description: String?,
    @SerializedName("display_order") val displayOrder: Int
)

data class ShopProductDto(
    val id: String,
    @SerializedName("brand_id") val brandId: String,
    @SerializedName("category_id") val categoryId: String?,
    val sku: String?,
    val name: String,
    val slug: String?,
    val description: String?,
    val condition: String,
    @SerializedName("age_min_months") val ageMinMonths: Int?,
    @SerializedName("age_max_months") val ageMaxMonths: Int?,
    @SerializedName("size_label") val sizeLabel: String?,
    val colour: String?,
    val price: Double,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("is_featured") val isFeatured: Boolean,
    val brands: EmbeddedNameDto?,
    val categories: EmbeddedNameDto?
)

data class EmbeddedNameDto(val name: String?)

data class StaffRoleDto(val role: String, @SerializedName("is_active") val isActive: Boolean)

data class ProductWriteDto(
    @SerializedName("brand_id") val brandId: String,
    @SerializedName("category_id") val categoryId: String?,
    val sku: String?,
    val name: String,
    val description: String?,
    val condition: String,
    @SerializedName("size_label") val sizeLabel: String?,
    val colour: String?,
    val price: Double,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("is_featured") val isFeatured: Boolean = false
)

data class InventoryWriteDto(
    @SerializedName("product_id") val productId: String,
    @SerializedName("quantity_on_hand") val quantityOnHand: Int,
    @SerializedName("quantity_reserved") val quantityReserved: Int = 0,
    @SerializedName("reorder_level") val reorderLevel: Int = 0
)

data class SignUpRequest(
    val email: String,
    val password: String,
    val data: Map<String, String>
)

data class SignInRequest(val email: String, val password: String)

data class AuthResponseDto(
    @SerializedName("access_token") val accessToken: String?,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("token_type") val tokenType: String?,
    @SerializedName("expires_in") val expiresIn: Long?,
    val user: AuthUserDto?
)

data class AuthUserDto(
    val id: String,
    val email: String?,
    @SerializedName("email_confirmed_at") val emailConfirmedAt: String?
)

data class OrderLineRequest(
    @SerializedName("product_id") val productId: String,
    val quantity: Int
)

data class PlaceCustomerOrderRequest(
    @SerializedName("p_brand_id") val brandId: String,
    @SerializedName("p_customer_name") val customerName: String,
    @SerializedName("p_contact_phone") val contactPhone: String,
    @SerializedName("p_delivery_address") val deliveryAddress: String,
    @SerializedName("p_delivery_city") val deliveryCity: String,
    @SerializedName("p_items") val items: List<OrderLineRequest>,
    @SerializedName("p_payment_method") val paymentMethod: String,
    @SerializedName("p_delivery_area") val deliveryArea: String? = null,
    @SerializedName("p_delivery_region") val deliveryRegion: String? = null,
    @SerializedName("p_digital_address") val digitalAddress: String? = null,
    @SerializedName("p_delivery_instructions") val deliveryInstructions: String? = null,
    @SerializedName("p_customer_notes") val customerNotes: String? = null
)

data class CustomerOrderDto(
    val id: String,
    @SerializedName("order_number") val orderNumber: String,
    @SerializedName("brand_id") val brandId: String,
    @SerializedName("customer_name") val customerName: String,
    val subtotal: Double,
    @SerializedName("delivery_fee") val deliveryFee: Double,
    @SerializedName("total_amount") val totalAmount: Double,
    @SerializedName("order_status") val orderStatus: String,
    @SerializedName("payment_status") val paymentStatus: String,
    @SerializedName("created_at") val createdAt: String
)
