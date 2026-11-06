package gh.royalglow.shop

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import gh.royalglow.shop.data.ProductWriteDto
import gh.royalglow.shop.data.ShopBrandDto
import gh.royalglow.shop.data.ShopCategoryDto
import gh.royalglow.shop.data.ShopProductDto
import gh.royalglow.shop.data.SupabaseShopRepository
import kotlinx.coroutines.launch
import retrofit2.HttpException

private val AdminRoyal = androidx.compose.ui.graphics.Color(0xFF7B176B)
private val AdminPink = androidx.compose.ui.graphics.Color(0xFFE91E83)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductManagementScreen(onCatalogChanged: () -> Unit) {
    val repository = remember { SupabaseShopRepository() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var staffRole by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    var brands by remember { mutableStateOf<List<ShopBrandDto>>(emptyList()) }
    var categories by remember { mutableStateOf<List<ShopCategoryDto>>(emptyList()) }
    var products by remember { mutableStateOf<List<ShopProductDto>>(emptyList()) }
    var selectedBrandId by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf("") }

    var editingProductId by remember { mutableStateOf<String?>(null) }
    var productName by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var stockText by remember { mutableStateOf("0") }
    var reorderText by remember { mutableStateOf("0") }
    var condition by remember { mutableStateOf("new") }
    var sizeLabel by remember { mutableStateOf("") }
    var colour by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var publishProduct by remember { mutableStateOf(false) }
    var featured by remember { mutableStateOf(false) }
    var formMessage by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        selectedImageUri = it
    }

    suspend fun refreshProducts() {
        products = repository.loadManagedProducts(accessToken)
        onCatalogChanged()
    }

    LaunchedEffect(accessToken, staffRole) {
        if (accessToken.isNotBlank() && staffRole in listOf("admin", "manager", "inventory")) {
            runCatching {
                brands = repository.loadBrands()
                if (selectedBrandId.isBlank()) selectedBrandId = brands.firstOrNull()?.id.orEmpty()
                products = repository.loadManagedProducts(accessToken)
            }.onFailure { loginError = readableError(it) }
        }
    }

    LaunchedEffect(selectedBrandId) {
        if (accessToken.isNotBlank() && selectedBrandId.isNotBlank()) {
            runCatching {
                categories = repository.loadCategories(selectedBrandId)
                if (categories.none { it.id == selectedCategoryId }) selectedCategoryId = ""
            }.onFailure { formMessage = readableError(it) }
        }
    }

    fun resetForm() {
        editingProductId = null
        productName = ""
        sku = ""
        description = ""
        priceText = ""
        stockText = "0"
        reorderText = "0"
        condition = "new"
        sizeLabel = ""
        colour = ""
        imageUrl = ""
        selectedImageUri = null
        publishProduct = false
        featured = false
        selectedCategoryId = ""
        formMessage = ""
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Inventory2, contentDescription = null, tint = AdminRoyal)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Product catalogue manager", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AdminRoyal)
                Text("Add products now and set prices whenever you are ready.", style = MaterialTheme.typography.bodySmall)
            }
        }

        if (accessToken.isBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = AdminRoyal)
                        Spacer(Modifier.width(8.dp))
                        Text("Staff sign-in required", fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Administrator / staff email") }, singleLine = true)
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    Button(
                        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                        onClick = {
                            scope.launch {
                                busy = true
                                loginError = ""
                                try {
                                    val auth = repository.signIn(email, password)
                                    val token = auth.accessToken.orEmpty()
                                    val id = auth.user?.id.orEmpty()
                                    if (token.isBlank() || id.isBlank()) {
                                        loginError = "Sign-in requires a confirmed account. Check your email verification and try again."
                                    } else {
                                        val role = repository.staffRole(id, token)?.role.orEmpty()
                                        if (role !in listOf("admin", "manager", "inventory")) {
                                            loginError = "This account does not have catalogue access. Ask the administrator to assign an inventory, manager or admin role."
                                            runCatching { repository.signOut(token) }
                                        } else {
                                            accessToken = token
                                            userId = id
                                            staffRole = role
                                        }
                                    }
                                } catch (e: Throwable) {
                                    loginError = readableError(e)
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (busy) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Sign in to manage products")
                    }
                    if (loginError.isNotBlank()) Text(loginError, color = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Signed in • ${staffRole.uppercase()}", color = AdminRoyal, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    val oldToken = accessToken
                    scope.launch { runCatching { repository.signOut(oldToken) } }
                    accessToken = ""
                    userId = ""
                    staffRole = ""
                    products = emptyList()
                    resetForm()
                }) { Text("Sign out") }
            }

            Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (editingProductId == null) "Add a product" else "Edit product", fontWeight = FontWeight.Bold, color = AdminRoyal)

                    Text("Choose shop", fontWeight = FontWeight.SemiBold)
                    var brandMenu by remember { mutableStateOf(false) }
                    val selectedBrand = brands.firstOrNull { it.id == selectedBrandId }
                    androidx.compose.material3.OutlinedButton(onClick = { brandMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Storefront, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(selectedBrand?.name ?: "Select brand")
                    }
                    DropdownMenu(expanded = brandMenu, onDismissRequest = { brandMenu = false }) {
                        brands.forEach { brandItem ->
                            DropdownMenuItem(text = { Text(brandItem.name) }, onClick = {
                                selectedBrandId = brandItem.id
                                selectedCategoryId = ""
                                brandMenu = false
                            })
                        }
                    }

                    var categoryMenu by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "No category selected",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        label = { Text("Category (optional)") },
                        trailingIcon = { TextButton(onClick = { categoryMenu = true }) { Text("Choose") } }
                    )
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        DropdownMenuItem(text = { Text("No category") }, onClick = { selectedCategoryId = ""; categoryMenu = false })
                        categories.forEach { category ->
                            DropdownMenuItem(text = { Text(category.name) }, onClick = { selectedCategoryId = category.id; categoryMenu = false })
                        }
                    }

                    OutlinedTextField(productName, { productName = it }, Modifier.fillMaxWidth(), label = { Text("Product name *") }, singleLine = true)
                    OutlinedTextField(sku, { sku = it }, Modifier.fillMaxWidth(), label = { Text("SKU / item code (optional)") }, singleLine = true)
                    OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 2)
                    OutlinedTextField(
                        priceText, { priceText = it; if (it.toDoubleOrNull()?.let { n -> n > 0 } == true) publishProduct = publishProduct },
                        Modifier.fillMaxWidth(), label = { Text("Selling price (GHS) — can be added later") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                    )
                    OutlinedTextField(stockText, { stockText = it }, Modifier.fillMaxWidth(), label = { Text("Opening stock quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(reorderText, { reorderText = it }, Modifier.fillMaxWidth(), label = { Text("Reorder alert level") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)

                    Text("Condition", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("new", "preloved", "refurbished").forEach { value ->
                            FilterChip(selected = condition == value, onClick = { condition = value }, label = { Text(value.replaceFirstChar { it.uppercase() }) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(sizeLabel, { sizeLabel = it }, Modifier.weight(1f), label = { Text("Size") }, singleLine = true)
                        OutlinedTextField(colour, { colour = it }, Modifier.weight(1f), label = { Text("Colour") }, singleLine = true)
                    }

                    OutlinedTextField(imageUrl, { imageUrl = it }, Modifier.fillMaxWidth(), label = { Text("Image URL (optional)") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { imagePicker.launch("image/*") }) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Choose product photo")
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(selectedImageUri?.lastPathSegment ?: "No photo selected", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = featured, onCheckedChange = { featured = it })
                        Text("Feature on storefront")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = publishProduct, onCheckedChange = { publishProduct = it })
                        Column {
                            Text("Publish to shoppers", fontWeight = FontWeight.SemiBold)
                            Text("Keep switched off to save as a draft until a price is entered.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Button(
                        enabled = !busy && productName.isNotBlank() && selectedBrandId.isNotBlank() &&
                            (stockText.toIntOrNull() ?: -1) >= 0 && (reorderText.toIntOrNull() ?: -1) >= 0 &&
                            (!publishProduct || (priceText.toDoubleOrNull()?.let { it > 0 } == true)),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                busy = true
                                formMessage = ""
                                try {
                                    val price = priceText.toDoubleOrNull() ?: 0.0
                                    val uploadedImage = selectedImageUri?.let { uri ->
                                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                            ?: error("Could not read the selected product photo.")
                                        val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
                                        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "product-image"
                                        repository.uploadProductImage(accessToken, fileName, bytes, contentType)
                                    }
                                    val record = ProductWriteDto(
                                        brandId = selectedBrandId,
                                        categoryId = selectedCategoryId.ifBlank { null },
                                        sku = sku.trim().ifBlank { null },
                                        name = productName.trim(),
                                        description = description.trim().ifBlank { null },
                                        condition = condition,
                                        sizeLabel = sizeLabel.trim().ifBlank { null },
                                        colour = colour.trim().ifBlank { null },
                                        price = price,
                                        imageUrl = uploadedImage ?: imageUrl.trim().ifBlank { null },
                                        isActive = publishProduct,
                                        isFeatured = featured
                                    )
                                    val saved = repository.saveProduct(accessToken, editingProductId, record)
                                    val targetId = editingProductId ?: saved?.id
                                        ?: error("The product may have saved, but the server did not return its ID.")
                                    repository.saveInventory(
                                        accessToken,
                                        targetId,
                                        stockText.toIntOrNull() ?: 0,
                                        reorderText.toIntOrNull() ?: 0
                                    )
                                    formMessage = if (publishProduct) "Product saved and published to the live catalogue." else "Product saved as a draft. Add a price and publish it when ready."
                                    resetForm()
                                    refreshProducts()
                                } catch (e: Throwable) {
                                    formMessage = readableError(e)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (editingProductId == null) "Save product" else "Update product")
                    }
                    if (busy) CircularProgressIndicator()
                    if (formMessage.isNotBlank()) Text(formMessage, color = if (formMessage.contains("saved", true)) AdminRoyal else MaterialTheme.colorScheme.error)
                }
            }

            Text("Your products", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AdminRoyal)
            if (products.isEmpty()) {
                Text("No products have been added yet. Use the form above to start your catalogue.")
            } else {
                products.forEach { product ->
                    Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(product.name, fontWeight = FontWeight.Bold)
                            Text(
                                "${brands.firstOrNull { it.id == product.brandId }?.name ?: "Shop"} • GHS ${"%.2f".format(product.price)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(if (product.isActive && product.price > 0) "Published" else "Draft / hidden from shoppers", color = if (product.isActive && product.price > 0) AdminRoyal else AdminPink, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                editingProductId = product.id
                                selectedBrandId = product.brandId
                                selectedCategoryId = product.categoryId.orEmpty()
                                productName = product.name
                                sku = product.sku.orEmpty()
                                description = product.description.orEmpty()
                                priceText = product.price.takeIf { it > 0 }?.toString().orEmpty()
                                condition = product.condition
                                sizeLabel = product.sizeLabel.orEmpty()
                                colour = product.colour.orEmpty()
                                imageUrl = product.imageUrl.orEmpty()
                                publishProduct = product.isActive
                                featured = product.isFeatured
                                selectedImageUri = null
                                formMessage = "Editing ${product.name}"
                            }) { Text("Edit product and price") }
                        }
                    }
                }
            }
        }
    }
}

private fun readableError(error: Throwable): String = when (error) {
    is HttpException -> error.response()?.errorBody()?.string()?.take(500)
        ?: "Server request failed (${error.code()})."
    else -> error.localizedMessage ?: "Something went wrong. Please try again."
}
