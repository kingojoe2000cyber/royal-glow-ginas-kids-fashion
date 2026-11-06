package gh.royalglow.shop.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * Thin application-facing layer over the Supabase REST/Auth endpoints.
 *
 * Keep the access token returned by signIn/signUp in a secure, app-managed session store.
 * Pass it only to authenticated operations. The public publishable key is not a substitute
 * for a customer session when calling customer checkout or private customer data endpoints.
 */
class SupabaseShopRepository(
    private val api: SupabaseApi = SupabaseApiFactory.api
) {
    suspend fun loadBrands(): List<ShopBrandDto> = api.getBrands()

    suspend fun loadCategories(brandId: String? = null): List<ShopCategoryDto> =
        api.getCategories(brandId = brandId?.let { "eq.$it" })

    suspend fun loadProducts(brandId: String? = null): List<ShopProductDto> =
        api.getProducts(brandId = brandId?.let { "eq.$it" })

    suspend fun loadManagedProducts(accessToken: String): List<ShopProductDto> =
        api.getManagedProducts(authorization = bearer(accessToken))

    suspend fun staffRole(userId: String, accessToken: String): StaffRoleDto? =
        api.getMyStaffRole(userId = "eq.$userId", authorization = bearer(accessToken))
            .firstOrNull { it.isActive }

    suspend fun saveProduct(
        accessToken: String,
        productId: String?,
        product: ProductWriteDto
    ): ShopProductDto? {
        val token = bearer(accessToken)
        return if (productId == null) {
            api.createProduct(product, token).firstOrNull()
        } else {
            api.updateProduct(productId = "eq.$productId", product = product, authorization = token)
            null
        }
    }

    suspend fun saveInventory(
        accessToken: String,
        productId: String,
        quantityOnHand: Int,
        reorderLevel: Int = 0
    ) {
        require(quantityOnHand >= 0) { "Stock quantity cannot be negative." }
        api.upsertInventory(
            inventory = InventoryWriteDto(
                productId = productId,
                quantityOnHand = quantityOnHand,
                reorderLevel = reorderLevel
            ),
            authorization = bearer(accessToken)
        )
    }

    suspend fun uploadProductImage(
        accessToken: String,
        fileName: String,
        bytes: ByteArray,
        contentType: String
    ): String {
        require(bytes.isNotEmpty()) { "The selected image is empty." }
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._/-]"), "_")
        val objectPath = "products/${UUID.randomUUID()}-$safeName"
        api.uploadProductImage(
            objectPath = objectPath,
            imageBytes = bytes.toRequestBody(contentType.toMediaType()),
            authorization = bearer(accessToken),
            contentType = contentType
        )
        return "${SupabaseConfig.BASE_URL}storage/v1/object/public/product-images/$objectPath"
    }

    suspend fun registerCustomer(
        fullName: String,
        phone: String,
        email: String,
        password: String
    ): AuthResponseDto {
        require(fullName.isNotBlank()) { "Full name is required." }
        require(phone.filter(Char::isDigit).length >= 9) { "A valid contact phone number is required." }
        require(email.isNotBlank()) { "Email address is required." }
        require(password.length >= 8) { "Password must contain at least 8 characters." }

        return api.signUp(
            SignUpRequest(
                email = email.trim(),
                password = password,
                data = mapOf("full_name" to fullName.trim(), "phone" to phone.trim())
            )
        )
    }

    suspend fun signIn(email: String, password: String): AuthResponseDto {
        require(email.isNotBlank()) { "Email address is required." }
        require(password.isNotBlank()) { "Password is required." }
        return api.signIn(request = SignInRequest(email.trim(), password))
    }

    suspend fun signOut(accessToken: String) {
        require(accessToken.isNotBlank()) { "A signed-in session is required." }
        api.signOut(bearer(accessToken))
    }

    suspend fun checkout(
        accessToken: String,
        request: PlaceCustomerOrderRequest
    ) = api.placeCustomerOrder(request, bearer(accessToken))

    suspend fun loadMyOrders(accessToken: String): List<CustomerOrderDto> {
        require(accessToken.isNotBlank()) { "Sign in to view your orders." }
        return api.getMyOrders(authorization = bearer(accessToken))
    }

    private fun bearer(token: String): String =
        if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"
}
