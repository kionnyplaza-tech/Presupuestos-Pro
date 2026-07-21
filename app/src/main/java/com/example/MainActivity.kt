package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import java.util.UUID

// --- DATA MODEL ---
data class QuoteItem(
    val id: String = UUID.randomUUID().toString(),
    var description: String = "",
    var quantity: String = "1",
    var unitPrice: String = "0.0"
)

data class Quote(
    var logoUri: Uri? = null,
    var issuerName: String = "",
    var issuerId: String = "",
    var issuerPhone: String = "",
    var issuerEmail: String = "",
    var clientName: String = "",
    var clientId: String = "",
    var clientAddress: String = "",
    var quoteNumber: String = "PRES-001",
    var quoteDate: String = "",
    var validityDays: String = "15",
    var items: List<QuoteItem> = listOf(QuoteItem()),
    var taxPercent: String = "0",
    var discountPercent: String = "0",
    var currency: String = "$",
    var notes: String = ""
) {
    val subtotal: Double
        get() = items.sumOf {
            val q = it.quantity.toIntOrNull() ?: 0
            val p = it.unitPrice.toDoubleOrNull() ?: 0.0
            q * p
        }
    val discountAmount: Double
        get() = subtotal * ((discountPercent.toDoubleOrNull() ?: 0.0) / 100.0)
    val afterDiscount: Double
        get() = subtotal - discountAmount
    val taxAmount: Double
        get() = afterDiscount * ((taxPercent.toDoubleOrNull() ?: 0.0) / 100.0)
    val grandTotal: Double
        get() = afterDiscount + taxAmount
}

class QuoteViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("quote_prefs", Context.MODE_PRIVATE)

    var quote by mutableStateOf(Quote(
        issuerName = prefs.getString("issuerName", "") ?: "",
        issuerId = prefs.getString("issuerId", "") ?: "",
        issuerPhone = prefs.getString("issuerPhone", "") ?: "",
        issuerEmail = prefs.getString("issuerEmail", "") ?: "",
        logoUri = prefs.getString("logoUri", null)?.let { Uri.parse(it) },
        clientId = UUID.randomUUID().toString().take(6).uppercase()
    ))
        private set

    fun updateQuote(newQuote: Quote, context: Context) {
        if (newQuote.logoUri != quote.logoUri && newQuote.logoUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    newQuote.logoUri!!,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore
            }
        }
        quote = newQuote
        prefs.edit()
            .putString("issuerName", newQuote.issuerName)
            .putString("issuerId", newQuote.issuerId)
            .putString("issuerPhone", newQuote.issuerPhone)
            .putString("issuerEmail", newQuote.issuerEmail)
            .putString("logoUri", newQuote.logoUri?.toString())
            .apply()
    }
}

// --- MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                QuoteApp()
            }
        }
    }
}

enum class AppScreen { FORM, PREVIEW }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteApp(viewModel: QuoteViewModel = viewModel()) {
    val quote = viewModel.quote
    val context = LocalContext.current
    val onQuoteUpdate: (Quote) -> Unit = { viewModel.updateQuote(it, context) }
    var currentScreen by remember { mutableStateOf(AppScreen.FORM) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (currentScreen == AppScreen.FORM) "Presupuestos Pro" else "Vista Previa", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (currentScreen == AppScreen.PREVIEW) {
                        IconButton(onClick = { currentScreen = AppScreen.FORM }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (currentScreen) {
                AppScreen.FORM -> FormScreen(quote, onQuoteUpdate, onNavigateToPreview = { currentScreen = AppScreen.PREVIEW })
                AppScreen.PREVIEW -> PreviewScreen(quote)
            }
        }
    }
}

// --- FORM SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScreen(quote: Quote, onQuoteUpdate: (Quote) -> Unit, onNavigateToPreview: () -> Unit) {
    val scrollState = rememberScrollState()
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) onQuoteUpdate(quote.copy(logoUri = uri)) }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Logo Upload
        OutlinedCard {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text("Logo de la Empresa", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (quote.logoUri != null) "Cambiar Logo" else "Subir Logo")
                }
                if (quote.logoUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = quote.logoUri,
                        contentDescription = "Logo",
                        modifier = Modifier.height(60.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        // Emisor
        OutlinedCard {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Datos del Emisor", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = quote.issuerName, onValueChange = { onQuoteUpdate(quote.copy(issuerName = it)) }, label = { Text("Nombre / Empresa") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = quote.issuerId, onValueChange = { onQuoteUpdate(quote.copy(issuerId = it)) }, label = { Text("RIF / Cédula / ID") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = quote.issuerPhone, onValueChange = { onQuoteUpdate(quote.copy(issuerPhone = it)) }, label = { Text("Teléfono") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = quote.issuerEmail, onValueChange = { onQuoteUpdate(quote.copy(issuerEmail = it)) }, label = { Text("Correo Electrónico") }, modifier = Modifier.fillMaxWidth())
            }
        }

        // Cliente
        OutlinedCard {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Datos del Cliente", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = quote.clientName, onValueChange = { onQuoteUpdate(quote.copy(clientName = it)) }, label = { Text("Nombre / Empresa") }, modifier = Modifier.fillMaxWidth())
            }
        }

        // Presupuesto Detalle
        OutlinedCard {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Detalles del Presupuesto", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = quote.quoteNumber, onValueChange = { onQuoteUpdate(quote.copy(quoteNumber = it)) }, label = { Text("Número") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = quote.quoteDate, onValueChange = { onQuoteUpdate(quote.copy(quoteDate = it)) }, label = { Text("Fecha") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = quote.validityDays, onValueChange = { onQuoteUpdate(quote.copy(validityDays = it)) }, label = { Text("Validez (días)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        }

        // Financial Config
        OutlinedCard {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Configuración Financiera", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currencies = listOf("$", "€", "£", "¥", "CHF", "CAD$", "AUD$", "MXN$", "COP$", "BRL R$", "ARS$", "CLP$", "PEN S/.", "INR ₹", "RUB ₽")
                    var expanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = quote.currency,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Moneda") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            currencies.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        onQuoteUpdate(quote.copy(currency = selectionOption))
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(value = quote.taxPercent, onValueChange = { onQuoteUpdate(quote.copy(taxPercent = it)) }, label = { Text("Impuesto %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = quote.discountPercent, onValueChange = { onQuoteUpdate(quote.copy(discountPercent = it)) }, label = { Text("Descuento %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
            }
        }

        // Items
        OutlinedCard {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Artículos / Servicios", style = MaterialTheme.typography.titleMedium)
                quote.items.forEachIndexed { index, item ->
                    ItemRow(
                        item = item,
                        onUpdate = { updated -> 
                            val newItems = quote.items.toMutableList()
                            newItems[index] = updated
                            onQuoteUpdate(quote.copy(items = newItems))
                        },
                        onRemove = {
                            val newItems = quote.items.toMutableList()
                            newItems.removeAt(index)
                            onQuoteUpdate(quote.copy(items = newItems))
                        }
                    )
                }
                TextButton(onClick = { 
                    val newItems = quote.items + QuoteItem()
                    onQuoteUpdate(quote.copy(items = newItems))
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(" Agregar Fila")
                }
            }
        }

        // Notes
        OutlinedCard {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notas / Términos", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = quote.notes,
                    onValueChange = { onQuoteUpdate(quote.copy(notes = it)) },
                    label = { Text("Condiciones de pago, notas adicionales...") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 5
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNavigateToPreview,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Ver Vista Previa", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun ItemRow(item: QuoteItem, onUpdate: (QuoteItem) -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = item.description,
            onValueChange = { onUpdate(item.copy(description = it)) },
            label = { Text("Desc.") },
            modifier = Modifier.weight(2f).padding(end = 4.dp),
            singleLine = true
        )
        OutlinedTextField(
            value = item.quantity,
            onValueChange = { onUpdate(item.copy(quantity = it)) },
            label = { Text("Cant") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f).padding(end = 4.dp),
            singleLine = true
        )
        OutlinedTextField(
            value = item.unitPrice,
            onValueChange = { onUpdate(item.copy(unitPrice = it)) },
            label = { Text("Precio") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f).padding(end = 4.dp),
            singleLine = true
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
        }
    }
}

// --- PREVIEW & EXPORT SCREEN ---
@Composable
fun PreviewScreen(quote: Quote) {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { uri ->
            if (uri != null) {
                try {
                    generatePdf(context, quote, uri)
                    Toast.makeText(context, "Presupuesto exportado a PDF", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al generar PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Surface(
            shadowElevation = 4.dp, 
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.Center) {
                Button(
                    onClick = { exportLauncher.launch("Presupuesto_${quote.quoteNumber}.pdf") },
                    modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Descargar PDF", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Simulated Paper View
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFECEFF1))
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Color(0xFF212121)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 32.dp, vertical = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Logo / Emisor Name
                        if (quote.logoUri != null) {
                            AsyncImage(
                                model = quote.logoUri,
                                contentDescription = "Logo",
                                modifier = Modifier.height(72.dp).widthIn(max = 150.dp),
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .height(72.dp)
                                    .width(120.dp)
                                    .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("LOGO", color = Color(0xFFBDBDBD), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            }
                        }
                        
                        // Doc Info
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "PRESUPUESTO",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Nº: ${quote.quoteNumber.ifBlank { "0000" }}", fontWeight = FontWeight.Bold)
                            Text("Fecha: ${quote.quoteDate.ifBlank { "DD/MM/AAAA" }}", color = Color(0xFF616161))
                            Text("Validez: ${quote.validityDays} días", color = Color(0xFF616161))
                        }
                    }
                    
                    Divider(color = Color(0xFFEEEEEE), thickness = 2.dp)
                    
                    // Emisor y Cliente
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("DE", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(quote.issuerName.ifBlank { "Nombre de tu Empresa" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (quote.issuerId.isNotBlank()) Text(quote.issuerId, color = Color(0xFF616161), fontSize = 14.sp)
                            if (quote.issuerPhone.isNotBlank()) Text(quote.issuerPhone, color = Color(0xFF616161), fontSize = 14.sp)
                            if (quote.issuerEmail.isNotBlank()) Text(quote.issuerEmail, color = Color(0xFF616161), fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PARA", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(quote.clientName.ifBlank { "Nombre del Cliente" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (quote.clientId.isNotBlank()) Text(quote.clientId, color = Color(0xFF616161), fontSize = 14.sp)
                            if (quote.clientAddress.isNotBlank()) Text(quote.clientAddress, color = Color(0xFF616161), fontSize = 14.sp)
                        }
                    }
                    
                    // Table
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                    ) {
                        // Table Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF5F7FA), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text("DESCRIPCIÓN", modifier = Modifier.weight(2.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF455A64))
                            Text("CANT.", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF455A64), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            Text("PRECIO", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF455A64), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            Text("TOTAL", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF455A64), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                        
                        // Table Items
                        quote.items.forEachIndexed { index, item ->
                            val q = item.quantity.toIntOrNull() ?: 0
                            val p = item.unitPrice.toDoubleOrNull() ?: 0.0
                            val rowBg = if (index % 2 == 0) Color.White else Color(0xFFFAFAFA)
                            
                            HorizontalDivider(color = Color(0xFFE0E0E0))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg)
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.description.ifBlank { "Artículo sin descripción" }, modifier = Modifier.weight(2.5f), fontSize = 14.sp)
                                Text(item.quantity, modifier = Modifier.weight(1f), fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                Text("${quote.currency}${String.format("%.2f", p)}", modifier = Modifier.weight(1.5f), fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                Text("${quote.currency}${String.format("%.2f", q * p)}", modifier = Modifier.weight(1.5f), fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                        }
                    }
                    
                    // Totals
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Column(modifier = Modifier.width(250.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Subtotal:", color = Color(0xFF616161), fontSize = 14.sp)
                                Text("${quote.currency}${String.format("%.2f", quote.subtotal)}", fontSize = 14.sp)
                            }
                            if (quote.discountAmount > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Descuento (${quote.discountPercent}%):", color = Color(0xFFD32F2F), fontSize = 14.sp)
                                    Text("-${quote.currency}${String.format("%.2f", quote.discountAmount)}", color = Color(0xFFD32F2F), fontSize = 14.sp)
                                }
                            }
                            if (quote.taxAmount > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Impuesto (${quote.taxPercent}%):", color = Color(0xFF616161), fontSize = 14.sp)
                                    Text("+${quote.currency}${String.format("%.2f", quote.taxAmount)}", fontSize = 14.sp)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFE0E0E0))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("TOTAL", fontWeight = FontWeight.Black, fontSize = 16.sp)
                                Text("${quote.currency}${String.format("%.2f", quote.grandTotal)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    
                    if (quote.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFF9C4).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFFFF59D), RoundedCornerShape(8.dp))
                                .padding(16.dp)
                        ) {
                            Text("NOTAS / TÉRMINOS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFF57F17), letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(quote.notes, color = Color(0xFF424242), fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- PDF GENERATOR ---
fun generatePdf(context: Context, quote: Quote, uri: Uri) {
    val pdfDocument = PdfDocument()
    // A4 Portrait Size (595 x 842 pt)
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas
    val paint = Paint().apply { color = android.graphics.Color.BLACK }

    var y = 50f
    val startX = 50f
    val rightX = 545f

    // Header Title
    paint.textSize = 28f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.color = android.graphics.Color.parseColor("#1976D2")
    canvas.drawText("PRESUPUESTO", startX, y, paint)

    // Header Info (Right)
    paint.color = android.graphics.Color.BLACK
    paint.textSize = 12f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.textAlign = Paint.Align.RIGHT
    canvas.drawText("Nº: ${quote.quoteNumber}", rightX, y, paint)
    y += 15f
    canvas.drawText("Fecha: ${quote.quoteDate}", rightX, y, paint)
    y += 15f
    canvas.drawText("Validez: ${quote.validityDays} días", rightX, y, paint)

    paint.textAlign = Paint.Align.LEFT
    y += 40f

    // Parties Box
    paint.textSize = 14f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.color = android.graphics.Color.DKGRAY
    canvas.drawText("De:", startX, y, paint)
    canvas.drawText("Para:", 300f, y, paint)
    y += 20f

    paint.textSize = 12f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.color = android.graphics.Color.BLACK
    canvas.drawText(quote.issuerName.ifEmpty { "Nombre/Empresa" }, startX, y, paint)
    canvas.drawText(quote.clientName.ifEmpty { "Nombre Cliente" }, 300f, y, paint)
    y += 15f
    canvas.drawText(quote.issuerId, startX, y, paint)
    canvas.drawText(quote.clientId, 300f, y, paint)
    y += 15f
    canvas.drawText(quote.issuerPhone, startX, y, paint)
    canvas.drawText(quote.clientAddress, 300f, y, paint)
    y += 15f
    canvas.drawText(quote.issuerEmail, startX, y, paint)

    y += 50f

    // Table Header
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("Descripción", startX, y, paint)
    canvas.drawText("Cant.", 370f, y, paint)
    canvas.drawText("Precio", 430f, y, paint)
    canvas.drawText("Total", 500f, y, paint)

    y += 10f
    paint.color = android.graphics.Color.LTGRAY
    canvas.drawLine(startX, y, rightX, y, paint)
    paint.color = android.graphics.Color.BLACK
    y += 20f

    // Table Items
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    for (item in quote.items) {
        val qty = item.quantity.toIntOrNull() ?: 0
        val price = item.unitPrice.toDoubleOrNull() ?: 0.0
        val total = qty * price

        canvas.drawText(item.description.take(30).ifEmpty { "-" }, startX, y, paint)
        canvas.drawText(qty.toString(), 370f, y, paint)
        canvas.drawText("${quote.currency}${String.format("%.2f", price)}", 430f, y, paint)
        canvas.drawText("${quote.currency}${String.format("%.2f", total)}", 500f, y, paint)
        y += 20f
    }

    y += 10f
    paint.color = android.graphics.Color.LTGRAY
    canvas.drawLine(startX, y, rightX, y, paint)
    paint.color = android.graphics.Color.BLACK
    y += 30f

    // Totals
    paint.textAlign = Paint.Align.RIGHT
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    canvas.drawText("Subtotal: ${quote.currency}${String.format("%.2f", quote.subtotal)}", rightX, y, paint)
    y += 20f
    if (quote.discountAmount > 0) {
        canvas.drawText("Descuento (${quote.discountPercent}%): -${quote.currency}${String.format("%.2f", quote.discountAmount)}", rightX, y, paint)
        y += 20f
    }
    if (quote.taxAmount > 0) {
        canvas.drawText("Impuesto (${quote.taxPercent}%): +${quote.currency}${String.format("%.2f", quote.taxAmount)}", rightX, y, paint)
        y += 20f
    }
    paint.textSize = 16f
    canvas.drawText("Total: ${quote.currency}${String.format("%.2f", quote.grandTotal)}", rightX, y, paint)

    paint.textAlign = Paint.Align.LEFT
    y += 60f

    // Notes
    if (quote.notes.isNotBlank()) {
        paint.textSize = 12f
        canvas.drawText("Notas/Términos:", startX, y, paint)
        y += 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = android.graphics.Color.DKGRAY
        val lines = quote.notes.split("\n")
        for (line in lines) {
            canvas.drawText(line, startX, y, paint)
            y += 15f
        }
    }

    pdfDocument.finishPage(page)
    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
        pdfDocument.writeTo(outputStream)
    }
    pdfDocument.close()
}
