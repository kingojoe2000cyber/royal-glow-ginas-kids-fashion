package gh.royalglow.shop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import gh.royalglow.shop.data.SupabaseShopRepository

private val Royal = Color(0xFF7B176B)
private val Pink = Color(0xFFE91E83)
private val Gold = Color(0xFFD6A72C)
private val Ink = Color(0xFF29132D)
private val money = NumberFormat.getCurrencyInstance(Locale("en", "GH")).apply { currency = java.util.Currency.getInstance("GHS") }

data class ShopProduct(val id: String, val name: String, val brand: String, val category: String, val price: Double, val stock: Int)
data class CartLine(val product: ShopProduct, val quantity: Int)

private val catalog = listOf(
    ShopProduct("demo-1", "Pre-loved kids dress", "Royal Glow", "Kids Thrift", 65.0, 12),
    ShopProduct("demo-2", "Children's T-shirt", "Gina's Kids Fashion", "Clothing", 45.0, 24),
    ShopProduct("demo-3", "Girls hair accessories set", "Royal Glow", "Girlie Goods", 25.0, 30),
    ShopProduct("demo-4", "Kids sneakers", "Gina's Kids Fashion", "Shoes", 95.0, 8),
    ShopProduct("demo-5", "Lip gloss", "Royal Glow", "Beauty", 20.0, 18),
    ShopProduct("demo-6", "Baby romper", "Gina's Kids Fashion", "Babywear", 55.0, 15)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RoyalGlowApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoyalGlowApp() {
    var tab by remember { mutableStateOf("Shop") }
    var brand by remember { mutableStateOf("All brands") }
    var query by remember { mutableStateOf("") }
    var cart by remember { mutableStateOf(listOf<CartLine>()) }
    var showCheckout by remember { mutableStateOf(false) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var deliveryAddress by remember { mutableStateOf("") }
    var orderPlaced by remember { mutableStateOf(false) }
    val repository = remember { SupabaseShopRepository() }
    val scope = rememberCoroutineScope()
    var liveProducts by remember { mutableStateOf<List<ShopProduct>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf("") }

    suspend fun refreshLiveCatalog() {
        catalogLoading = true
        catalogError = ""
        try {
            liveProducts = repository.loadProducts()
                .filter { it.price > 0.0 && it.isActive }
                .map { item ->
                    ShopProduct(
                        id = item.id,
                        name = item.name,
                        brand = item.brands?.name ?: "Shop",
                        category = item.categories?.name ?: "General",
                        price = item.price,
                        stock = 0
                    )
                }
        } catch (e: Throwable) {
            catalogError = e.localizedMessage ?: "Unable to load the catalogue. Check your internet connection."
        } finally {
            catalogLoading = false
        }
    }

    LaunchedEffect(Unit) { refreshLiveCatalog() }

    val total = cart.sumOf { it.product.price * it.quantity }
    val products = liveProducts.filter {
        (brand == "All brands" || it.brand == brand) &&
            (query.isBlank() || it.name.contains(query, true) || it.category.contains(query, true))
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Royal, secondary = Pink, tertiary = Gold, background = Color(0xFFFFF8FC))) {
        Scaffold(
            topBar = { TopAppBar(title = { Column { Text("ROYAL GLOW", fontWeight = FontWeight.Black, color = Royal, fontSize = 19.sp); Text("& GINA'S KIDS FASHION", fontSize = 10.sp, color = Ink, letterSpacing = 1.sp) } }, actions = { BadgedBox(badge = { if (cart.isNotEmpty()) Badge { Text(cart.sumOf { it.quantity }.toString()) } }) { IconButton(onClick = { tab = "Cart" }) { Icon(Icons.Default.ShoppingBag, "Cart", tint = Royal) } } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)) },
            bottomBar = { NavigationBar(containerColor = Color.White) { listOf("Shop", "Cart", "Orders", "POS", "Admin").forEach { item -> NavigationBarItem(selected = tab == item, onClick = { tab = item }, icon = { Icon(when(item) { "Shop" -> Icons.Default.Storefront; "Cart" -> Icons.Default.ShoppingCart; "Orders" -> Icons.Default.ReceiptLong; "POS" -> Icons.Default.PointOfSale; else -> Icons.Default.Dashboard }, item) }, label = { Text(item) }) } } }
        ) { padding ->
            Box(Modifier.fillMaxSize().background(Color(0xFFFFF8FC)).padding(padding)) {
                when (tab) {
                    "Shop" -> ShopScreen(
                        products, brand, { brand = it }, query, { query = it },
                        loading = catalogLoading, error = catalogError,
                        onRefresh = { scope.launch { refreshLiveCatalog() } },
                        onAdd = { p ->
                            val old = cart.find { it.product.id == p.id }
                            cart = if (old == null) cart + CartLine(p, 1)
                            else cart.map { if (it.product.id == p.id) it.copy(quantity = it.quantity + 1) else it }
                        }
                    )
                    "Cart" -> CartScreen(cart, total, onChange = { id, delta -> cart = cart.mapNotNull { line -> if (line.product.id != id) line else line.copy(quantity = line.quantity + delta).takeIf { it.quantity > 0 } } }, onCheckout = { showCheckout = true })
                    "Orders" -> OrdersScreen(orderPlaced)
                    "POS" -> PosScreen()
                    else -> ProductManagementScreen(onCatalogChanged = {
                        scope.launch { refreshLiveCatalog() }
                    })
                }
            }
        }
        if (showCheckout) {
            AlertDialog(
                onDismissRequest = { showCheckout = false },
                title = { Text("Delivery & checkout") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(customerName, { customerName = it }, label = { Text("Customer name") }, singleLine = true)
                    OutlinedTextField(customerPhone, { customerPhone = it }, label = { Text("Contact phone number") }, placeholder = { Text("e.g. 024 XXX XXXX") }, singleLine = true)
                    OutlinedTextField(deliveryAddress, { deliveryAddress = it }, label = { Text("Full delivery address") }, placeholder = { Text("House number, street, area, town/city and delivery directions") }, minLines = 2)
                    Text("Order total: ${money.format(total)}", fontWeight = FontWeight.Bold, color = Royal)
                    Text("Checkout is being connected to customer sign-in and secure order submission. No order or payment will be submitted from this screen yet.", fontSize = 12.sp, color = Color.Gray)
                } },
                confirmButton = { Button(enabled = false, onClick = {}) { Text("Checkout coming soon") } },
                dismissButton = { TextButton(onClick = { showCheckout = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun ShopScreen(
    products: List<ShopProduct>,
    brand: String,
    onBrand: (String) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    loading: Boolean,
    error: String,
    onRefresh: () -> Unit,
    onAdd: (ShopProduct) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Column(Modifier.fillMaxWidth().background(Royal, RoundedCornerShape(22.dp)).padding(18.dp)) { Text("Little looks. Big smiles!", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black); Text("Trendy kidswear, thrift finds & girly essentials", color = Color.White.copy(alpha = .9f)); Spacer(Modifier.height(8.dp)); Text("Shop smart. Dress smart.", color = Gold, fontWeight = FontWeight.Bold) } }
        item { OutlinedTextField(query, onQuery, Modifier.fillMaxWidth(), label = { Text("Search products") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All brands", "Royal Glow", "Gina's Kids Fashion").forEach { b -> FilterChip(selected = brand == b, onClick = { onBrand(b) }, label = { Text(if (b == "Gina's Kids Fashion") "Gina's" else b) }) } } }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Shop the live catalogue", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
        if (loading) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Loading products…", color = Color.Gray) } }
        if (error.isNotBlank()) item { Text(error, color = Color(0xFFB00020), fontSize = 12.sp) }
        if (!loading && products.isEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("New arrivals coming soon", color = Royal, fontWeight = FontWeight.Bold)
                    Text("There are no priced products published yet. Check back soon or contact the shop for current availability.", color = Color.Gray)
                }
            }
        }
        items(products) { p -> Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(68.dp).background(Color(0xFFFCE4F1), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(if (p.category == "Beauty" || p.category == "Girlie Goods") Icons.Default.AutoAwesome else Icons.Default.Checkroom, null, tint = Royal, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(p.name, fontWeight = FontWeight.Bold, color = Ink); Text("${p.brand} • ${p.category}", fontSize = 11.sp, color = Color.Gray); Text(money.format(p.price), color = Royal, fontWeight = FontWeight.Bold); Text("Available in store", fontSize = 10.sp, color = Color.Gray) }
            Button(onClick = { onAdd(p) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Icon(Icons.Default.Add, null); Text("Add") }
        } } }
    }
}

@Composable
private fun CartScreen(cart: List<CartLine>, total: Double, onChange: (String, Int) -> Unit, onCheckout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Your shopping cart", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Royal); Spacer(Modifier.height(12.dp))
        if (cart.isEmpty()) Text("Your cart is empty. Browse the shop to add items.", color = Color.Gray) else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(cart) { line -> Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(line.product.name, fontWeight = FontWeight.Bold); Text("${money.format(line.product.price)} × ${line.quantity}") }; IconButton(onClick = { onChange(line.product.id, -1) }) { Icon(Icons.Default.Remove, "Decrease") }; Text(line.quantity.toString()); IconButton(onClick = { onChange(line.product.id, 1) }) { Icon(Icons.Default.Add, "Increase") } } } } }
            HorizontalDivider(); Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Total", fontWeight = FontWeight.Bold); Text(money.format(total), fontWeight = FontWeight.Bold, color = Royal) }
            Button(onClick = onCheckout, Modifier.fillMaxWidth()) { Text("Proceed to checkout") }
        }
    }
}

@Composable
private fun OrdersScreen(orderPlaced: Boolean) {
    Column(Modifier.fillMaxSize().padding(20.dp)) { Text("My orders", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Royal); Spacer(Modifier.height(12.dp)); if (orderPlaced) Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(16.dp)) { Text("Order received", fontWeight = FontWeight.Bold, color = Royal); Text("Your demo order has been recorded in this session."); Text("Status: Pending confirmation", color = Gold) } } else Text("No orders yet. Your placed orders will appear here.", color = Color.Gray) }
}

@Composable
private fun PosScreen() {
    var selected by remember { mutableStateOf(catalog.first()) }; var quantity by remember { mutableIntStateOf(1) }; var saleComplete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Point of Sale", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Royal); Text("Quick counter sale", color = Color.Gray); Text("Select product", fontWeight = FontWeight.Bold)
        catalog.forEach { p -> Row(Modifier.fillMaxWidth().clickable { selected = p }.background(if (selected.id == p.id) Color(0xFFFCE4F1) else Color.White, RoundedCornerShape(12.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(p.name); Text(money.format(p.price), color = Royal) } }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Quantity: "); IconButton(onClick = { if (quantity > 1) quantity-- }) { Icon(Icons.Default.Remove, null) }; Text("$quantity"); IconButton(onClick = { quantity++ }) { Icon(Icons.Default.Add, null) } }
        Text("Total: ${money.format(selected.price * quantity)}", fontWeight = FontWeight.Bold); Button(onClick = { saleComplete = true; quantity = 1 }, Modifier.fillMaxWidth()) { Text("Complete cash sale") }; if (saleComplete) Text("Demo sale completed. Connect the live database to save transactions.", color = Royal)
    }
}

@Composable
private fun AdminScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Store dashboard", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Royal); Text("Combined overview • demo data", color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricCard("Products", catalog.size.toString(), Modifier.weight(1f)); MetricCard("Stock units", catalog.sumOf { it.stock }.toString(), Modifier.weight(1f)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricCard("Brands", "2", Modifier.weight(1f)); MetricCard("Currency", "GHS", Modifier.weight(1f)) }
        Text("Management areas", fontWeight = FontWeight.Bold)
        listOf("Product & category management", "Inventory and stock adjustments", "Supplier and purchase records", "Sales and expense tracking", "Staff accounts and permissions", "Daily / monthly reports").forEach { Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = Pink); Spacer(Modifier.width(10.dp)); Text(it) } } }
        Text("Administrator authentication and live data sync must be configured before production use.", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(14.dp)) { Text(label, color = Color.Gray, fontSize = 12.sp); Text(value, color = Royal, fontSize = 24.sp, fontWeight = FontWeight.Black) } }
}
