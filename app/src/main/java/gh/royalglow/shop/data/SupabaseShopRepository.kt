package gh.royalglow.shop.data

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
