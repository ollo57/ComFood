package com.example.comfood.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.comfood.BuildConfig
import com.example.comfood.data.ComFoodRepository
import com.example.comfood.data.FoodLogEntry
import com.example.comfood.data.FoodKnowledgeLoader
import com.example.comfood.data.IngredientSection
import com.example.comfood.data.IngredientRule
import com.example.comfood.data.MacroEstimate
import com.example.comfood.data.MealCandidate
import com.example.comfood.data.MealComposition
import com.example.comfood.data.MealLookupResult
import com.example.comfood.data.NutritionEstimate
import com.example.comfood.data.OpenFoodFactsService
import com.example.comfood.data.PendingMealApproval
import com.example.comfood.data.ProductReview
import com.example.comfood.data.ResolvedFoodItem
import com.example.comfood.data.ResolvedIngredient
import com.example.comfood.data.UsdaFoodDataCentralService
import com.example.comfood.data.WearPendingMealSync
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private enum class Screen(val label: String, val icon: ImageVector) {
    Voice("Voice", Icons.Default.Mic),
    Scanner("Scanner", Icons.Default.CameraAlt),
    Log("Log", Icons.AutoMirrored.Filled.MenuBook),
    Nutrition("Nutrition", Icons.Default.CalendarMonth)
}

private enum class LogWindow { Daily, Weekly }

private val voiceBackground = Brush.verticalGradient(
    colors = listOf(Color(0xFF0A1A0F), Color(0xFF112318), Color(0xFF0A1A0F))
)
private val scannerBackground = Brush.verticalGradient(
    colors = listOf(Color(0xFF0A1A0F), Color(0xFF112318), Color(0xFF0A1A0F))
)
private val logBackground = Brush.verticalGradient(
    colors = listOf(Color(0xFF0A1A0F), Color(0xFF112318), Color(0xFF0A1A0F))
)
private val spotifyGreen = Color(0xFF1DB954)
private val mintTint = Color(0xFFD4F5E2)
private val nearBlackGreen = Color(0xFF0A1A0F)
private val darkCard = Color(0xFF112318)
private val elevatedSurface = Color(0xFF1A2E1F)
private val textPrimary = Color(0xFFE8F5EC)
private val textSecondary = Color(0xFF8BAB94)
private val textTertiary = Color(0xFF4D7257)
private val accentBright = Color(0xFF57D68A)
private val softRed = Color(0xFFA32D2D)
private val blush = Color(0xFF36181B)
private val darkAmber = Color(0xFFE6C77D)
private val paleAmber = Color(0xFF3A301A)
private val calorieAccent = Color(0xFF57D68A)
private val proteinAccent = Color(0xFF2DDE7A)
private val carbsAccent = Color(0xFF1DB954)
private val fatAccent = Color(0xFF8BAB94)
private val terracotta = spotifyGreen
private val warmCream = elevatedSurface
private val forestGreen = spotifyGreen
private val sageGreen = mintTint.copy(alpha = 0.15f)
private val deepNavy = nearBlackGreen
private val calorieTerracotta = calorieAccent
private val proteinBlue = proteinAccent
private val carbsTeal = carbsAccent
private val fatPurple = fatAccent

@Suppress("NewApi")
@Composable
fun ComFoodApp(activity: Activity) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { ComFoodRepository(context) }
    val knowledgeLoader = remember { FoodKnowledgeLoader(context) }
    val brandProfiles = remember { knowledgeLoader.loadBrandProfiles() }
    val ingredientRules = remember { knowledgeLoader.loadIngredientRules() }
    val localMenuItems = remember { knowledgeLoader.loadLocalMenuItems() }
    val usdaService = remember { UsdaFoodDataCentralService(BuildConfig.USDA_API_KEY) }
    val service = remember {
        OpenFoodFactsService(
            brandProfiles = brandProfiles,
            localMenuItems = localMenuItems,
            ingredientRules = ingredientRules,
            usdaService = usdaService
        )
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedScreen by rememberSaveable { mutableStateOf(Screen.Voice) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var logWindow by rememberSaveable { mutableStateOf(LogWindow.Daily) }
    val logEntries = remember { mutableStateListOf<FoodLogEntry>().apply { addAll(repository.loadEntries()) } }
    val pendingApprovals = remember {
        mutableStateListOf<PendingMealApproval>().apply { addAll(repository.loadPendingApprovals()) }
    }
    val avoidedIngredients = remember { mutableStateOf(repository.loadAvoidedIngredients()) }
    var lastPendingCount by remember { mutableIntStateOf(pendingApprovals.size) }
    var showSuccessDialog by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(repository) {
        val listener = repository.registerChangeListener {
            logEntries.clear()
            logEntries.addAll(repository.loadEntries())
            pendingApprovals.clear()
            pendingApprovals.addAll(repository.loadPendingApprovals())
            avoidedIngredients.value = repository.loadAvoidedIngredients()
        }
        onDispose { repository.unregisterChangeListener(listener) }
    }

    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                WearPendingMealSync.syncFromWear(context)
            }
        }
        pendingApprovals.clear()
        pendingApprovals.addAll(repository.loadPendingApprovals())
    }

    DisposableEffect(lifecycleOwner, repository) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            WearPendingMealSync.syncFromWear(context)
                        }
                    }
                    pendingApprovals.clear()
                    pendingApprovals.addAll(repository.loadPendingApprovals())
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(pendingApprovals.size) {
        if (pendingApprovals.size > lastPendingCount) {
            selectedScreen = Screen.Log
        }
        lastPendingCount = pendingApprovals.size
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = nearBlackGreen,
        bottomBar = {
            NavigationBar(
                containerColor = darkCard,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        icon = {
                            Icon(
                                screen.icon,
                                contentDescription = screen.label,
                                tint = if (selectedScreen == screen) spotifyGreen else textTertiary
                            )
                        },
                        label = {
                            Text(
                                screen.label,
                                color = if (selectedScreen == screen) textPrimary else textTertiary
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = elevatedSurface,
                            selectedIconColor = spotifyGreen,
                            selectedTextColor = textPrimary,
                            unselectedIconColor = textTertiary,
                            unselectedTextColor = textTertiary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        if (showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { showSuccessDialog = false },
                confirmButton = {
                    TextButton(onClick = { showSuccessDialog = false }) {
                        Text("OK", color = spotifyGreen)
                    }
                },
                title = { Text("Logged", color = textPrimary) },
                text = { Text("Item has been catalogued", color = textPrimary) },
                containerColor = elevatedSurface
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .safeDrawingPadding(),
            color = nearBlackGreen
        ) {
            AnimatedContent(
                targetState = selectedScreen,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(animationSpec = tween(350)) { it * direction } + fadeIn(animationSpec = tween(350)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(350)) { -it * direction } + fadeOut(animationSpec = tween(350)))
                },
                label = "ScreenTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    Screen.Voice -> VoiceScreen(
                        service = service,
                        latestLoggedEntry = logEntries.firstOrNull(),
                        onLog = { entry ->
                            logEntries.add(0, entry)
                            repository.saveEntries(logEntries)
                            showSuccessDialog = true
                        },
                        snackbarHostState = snackbarHostState
                    )

                    Screen.Scanner -> ScannerScreen(
                        activity = activity,
                        service = service,
                        ingredientRules = ingredientRules,
                        avoidedIngredients = avoidedIngredients,
                        onAvoidedIngredientsChanged = { selected ->
                            avoidedIngredients.value = selected
                            repository.saveAvoidedIngredients(selected)
                        },
                        onLog = { entry ->
                            logEntries.add(0, entry)
                            repository.saveEntries(logEntries)
                            showSuccessDialog = true
                        },
                        snackbarHostState = snackbarHostState
                    )

                    Screen.Log -> LogScreen(
                        entries = logEntries,
                        selectedDate = selectedDate,
                        logWindow = logWindow,
                        onSelectedDateChange = { selectedDate = it },
                        onLogWindowChange = { logWindow = it },
                        pendingApprovals = pendingApprovals,
                        onApprovePending = { pending, candidate ->
                            val resolvedComposition = candidate.mealComposition
                                ?: pending.candidates.firstNotNullOfOrNull { it.mealComposition }
                            resolvedMacros(pending.candidates, resolvedComposition)?.let { macros ->
                                logEntries.add(
                                    0,
                                    FoodLogEntry(
                                        id = UUID.randomUUID().toString(),
                                        title = compositionTitle(resolvedComposition, pending.originalQuery.ifBlank { candidate.product.name }),
                                        source = compositionSource(resolvedComposition, candidate.product.sourceUrl),
                                        timestampUtcMillis = pending.timestampUtcMillis,
                                        macros = macros,
                                        nutrition = resolvedNutrition(pending.candidates, resolvedComposition)
                                    )
                                )
                            }
                            pendingApprovals.removeAll { it.id == pending.id }
                            repository.saveEntries(logEntries)
                            repository.savePendingApprovals(pendingApprovals)
                            showSuccessDialog = true
                        },
                        onDismissPending = { pending ->
                            pendingApprovals.removeAll { it.id == pending.id }
                            repository.savePendingApprovals(pendingApprovals)
                        },
                        onDeleteEntry = { entry ->
                            logEntries.removeAll { it.id == entry.id }
                            repository.saveEntries(logEntries)
                        },
                        onExport = {
                            scope.launch {
                                val exportFile = withContext(Dispatchers.IO) {
                                    repository.writeExport(logEntries, activity.cacheDir.resolve("exports"))
                                }
                                shareExport(activity, exportFile)
                            }
                        }
                    )

                    Screen.Nutrition -> NutritionScreen(
                        entries = logEntries,
                        selectedDate = selectedDate,
                        onSelectedDateChange = { selectedDate = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceScreen(
    service: OpenFoodFactsService,
    latestLoggedEntry: FoodLogEntry?,
    onLog: (FoodLogEntry) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mealDescription by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var lastEstimate by remember { mutableStateOf<MealLookupResult.Success?>(null) }

    fun estimate(description: String) {
        scope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) { service.estimateMeal(description) }
            isLoading = false
            when (result) {
                is MealLookupResult.Success -> lastEstimate = result
                is MealLookupResult.Failure -> snackbarHostState.showSnackbar(result.message)
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            mealDescription = spoken
            estimate(spoken)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(voiceBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Voice Food Logger",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tell me what you ate",
                        color = textSecondary
                    )
                    Text(
                        "Offline-first food resolution with structured meal decomposition",
                        color = forestGreen,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .size(176.dp)
                        .clip(CircleShape)
                        .background(terracotta),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { launchSpeechInput(context, speechLauncher) },
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = textPrimary
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(42.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Tap to Record", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        color = terracotta
                    )
                }
            }

            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = textTertiary,
                            modifier = Modifier.size(42.dp)
                        )
                        if (lastEstimate == null) {
                            Text("No meals logged yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tap the microphone to get started",
                                color = textSecondary
                            )
                        } else {
                            val composition = lastEstimate?.composition
                                ?: lastEstimate?.candidates?.firstNotNullOfOrNull { it.mealComposition }
                            Text(
                                compositionTitle(composition, mealDescription.ifBlank { "Resolved meal" }),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Resolved foods and ingredients are shown below with confidence and calorie estimates.",
                                color = textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (mealDescription.isNotBlank()) {
                item {
                    OutlinedTextField(
                        value = mealDescription,
                        onValueChange = { mealDescription = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Captured meal") }
                    )
                }
            }

            lastEstimate?.let { estimate ->
                val composition = estimate.composition
                    ?: estimate.candidates.firstNotNullOfOrNull { it.mealComposition }
                if (composition != null) {
                    item {
                        Text(
                            "Resolved Meal",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        MealCompositionCard(
                            composition = composition,
                            mealMacros = resolvedMacros(estimate.candidates, composition),
                            onLogFood = { food ->
                                val macros = resolvedFoodMacros(food, estimate.candidates) ?: return@MealCompositionCard
                                onLog(
                                    FoodLogEntry(
                                        id = UUID.randomUUID().toString(),
                                        title = food.label,
                                        source = compositionSource(composition, "Food Resolution Engine"),
                                        timestampUtcMillis = System.currentTimeMillis(),
                                        macros = macros,
                                        nutrition = resolvedFoodNutrition(food, estimate.candidates)
                                    )
                                )
                            },
                            onLogIngredient = { ingredient ->
                                val macros = resolvedIngredientMacros(ingredient) ?: return@MealCompositionCard
                                onLog(
                                    FoodLogEntry(
                                        id = UUID.randomUUID().toString(),
                                        title = ingredientLogTitle(ingredient, composition),
                                        source = compositionSource(composition, "Food Resolution Engine"),
                                        timestampUtcMillis = System.currentTimeMillis(),
                                        macros = macros,
                                        nutrition = ingredient.estimatedNutrition
                                    )
                                )
                            }
                        )
                    }
                    item {
                        val resolvedMacros = resolvedMacros(estimate.candidates, composition)
                        val resolvedNutrition = resolvedNutrition(estimate.candidates, composition)
                        Button(
                            onClick = {
                                val macros = resolvedMacros ?: return@Button
                                onLog(
                                    FoodLogEntry(
                                        id = UUID.randomUUID().toString(),
                                        title = compositionTitle(composition, mealDescription.ifBlank { "Resolved meal" }),
                                        source = compositionSource(composition, "Food Resolution Engine"),
                                        timestampUtcMillis = System.currentTimeMillis(),
                                        macros = macros,
                                        nutrition = resolvedNutrition
                                    )
                                )
                            },
                            enabled = resolvedMacros != null,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = forestGreen)
                        ) {
                            Text("Log Resolved Meal")
                        }
                    }
                }
            }

            latestLoggedEntry?.let { entry ->
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Most recent entry", fontWeight = FontWeight.SemiBold, color = textSecondary)
                            Text(entry.title, style = MaterialTheme.typography.titleMedium, color = textPrimary)
                            Text(entry.timestampUtcMillis.toDisplayTime(), color = textSecondary)
                            MacroRow(entry.macros)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerScreen(
    activity: Activity,
    service: OpenFoodFactsService,
    ingredientRules: List<IngredientRule>,
    avoidedIngredients: MutableState<Set<String>>,
    onAvoidedIngredientsChanged: (Set<String>) -> Unit,
    onLog: (FoodLogEntry) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    var barcode by rememberSaveable { mutableStateOf("") }
    var review by remember { mutableStateOf<ProductReview?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showMoreFilters by rememberSaveable { mutableStateOf(false) }
    val commonAvoidedIngredients = remember(ingredientRules) { ingredientRules.filter { it.isCommon } }
    val moreAvoidedIngredients = remember(ingredientRules) { ingredientRules.filterNot { it.isCommon } }
    val scanner = remember {
        GmsBarcodeScanning.getClient(
            activity,
            GmsBarcodeScannerOptions.Builder().enableAutoZoom().build()
        )
    }

    fun lookup(code: String) {
        scope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) { service.lookupBarcode(code) }
            isLoading = false
            result.fold(
                onSuccess = { product ->
                    val blocked = blockedIngredients(
                        product.ingredientsText,
                        avoidedIngredients.value,
                        ingredientRules
                    )
                    review = ProductReview(product, blocked)
                },
                onFailure = { error ->
                    snackbarHostState.showSnackbar(error.message ?: "Lookup failed.")
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scannerBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Barcode Scanner",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Menu, contentDescription = null)
                    }
                }
            }

            item {
                AppCard(modifier = Modifier.fillMaxWidth(), innerPadding = 0.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(440.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(deepNavy),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.72f)
                                    .height(108.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.Transparent)
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(deepNavy)
                                        .padding(horizontal = 18.dp, vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            tint = textTertiary,
                                            modifier = Modifier.size(42.dp)
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Text("Position barcode in frame", color = textPrimary)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0x6600FFAA), Color.Transparent)
                                            )
                                        )
                                )
                            }
                        }

                        Button(
                            onClick = {
                                scanner.startScan()
                                    .addOnSuccessListener { scanned ->
                                        barcode = scanned.rawValue.orEmpty()
                                        if (barcode.isNotBlank()) lookup(barcode)
                                    }
                                    .addOnFailureListener { error ->
                                        if (error is ApiException &&
                                            error.statusCode == CommonStatusCodes.CANCELED
                                        ) {
                                            return@addOnFailureListener
                                        }
                                        Toast.makeText(
                                            activity,
                                            "Scanner unavailable right now.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = forestGreen)
                        ) {
                            Text("Scan Barcode")
                        }
                    }
                }
            }

            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = barcode,
                            onValueChange = { barcode = it.filter(Char::isDigit) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Barcode") },
                            placeholder = { Text("Scan or type UPC / EAN") }
                        )
                        Button(
                            onClick = { lookup(barcode) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = terracotta)
                        ) {
                            Text("Check Product")
                        }
                        if (isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = terracotta
                            )
                        }
                    }
                }
            }

            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Common avoided ingredients",
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                        commonAvoidedIngredients.forEach { ingredient ->
                            IngredientCheckboxRow(
                                ingredient = ingredient,
                                selected = ingredient.key in avoidedIngredients.value,
                                onCheckedChange = { checked ->
                                    val updated = avoidedIngredients.value.toMutableSet().apply {
                                        if (checked) add(ingredient.key) else remove(ingredient.key)
                                    }
                                    onAvoidedIngredientsChanged(updated)
                                    review = review?.copy(
                                        blockedIngredients = blockedIngredients(
                                            review?.product?.ingredientsText,
                                            updated,
                                            ingredientRules
                                        )
                                    )
                                }
                            )
                        }
                        Button(
                            onClick = { showMoreFilters = !showMoreFilters },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (showMoreFilters) sageGreen else warmCream,
                                contentColor = textPrimary
                            )
                        ) {
                            Text(if (showMoreFilters) "Hide More Filters" else "More Ingredients + Allergens")
                        }
                        if (showMoreFilters) {
                            moreAvoidedIngredients
                                .groupBy { it.section }
                                .forEach { (section, ingredients) ->
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            sectionTitle(section),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        ingredients.forEach { ingredient ->
                                            IngredientCheckboxRow(
                                                ingredient = ingredient,
                                                selected = ingredient.key in avoidedIngredients.value,
                                                onCheckedChange = { checked ->
                                                    val updated = avoidedIngredients.value.toMutableSet().apply {
                                                        if (checked) add(ingredient.key) else remove(ingredient.key)
                                                    }
                                                    onAvoidedIngredientsChanged(updated)
                                                    review = review?.copy(
                                                        blockedIngredients = blockedIngredients(
                                                            review?.product?.ingredientsText,
                                                            updated,
                                                            ingredientRules
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                        }
                    }
                }
            }

            review?.let { reviewState ->
                item {
                    ProductReviewCard(review = reviewState, onLog = {
                        val macros = reviewState.product.macrosPer100g ?: return@ProductReviewCard
                        onLog(
                            FoodLogEntry(
                                id = UUID.randomUUID().toString(),
                                title = reviewState.product.name,
                                source = reviewState.product.sourceUrl,
                                timestampUtcMillis = System.currentTimeMillis(),
                                macros = macros,
                                nutrition = reviewState.product.nutritionPerServing
                            )
                        )
                    })
                }
            }
        }
    }
}

@Suppress("NewApi")
@Composable
private fun LogScreen(
    entries: List<FoodLogEntry>,
    selectedDate: LocalDate,
    logWindow: LogWindow,
    onSelectedDateChange: (LocalDate) -> Unit,
    onLogWindowChange: (LogWindow) -> Unit,
    pendingApprovals: List<PendingMealApproval>,
    onApprovePending: (PendingMealApproval, MealCandidate) -> Unit,
    onDismissPending: (PendingMealApproval) -> Unit,
    onDeleteEntry: (FoodLogEntry) -> Unit = {},
    onExport: () -> Unit
) {
    val filteredEntries by remember(selectedDate, logWindow) {
        derivedStateOf {
            when (logWindow) {
                LogWindow.Daily -> entries.filter { it.timestampUtcMillis.toLocalDate() == selectedDate }
                LogWindow.Weekly -> {
                    val start = selectedDate.minusDays(selectedDate.dayOfWeek.value.toLong() - 1L)
                    val end = start.plusDays(6)
                    entries.filter {
                        val date = it.timestampUtcMillis.toLocalDate()
                        date in start..end
                    }
                }
            }
        }
    }
    val summary by remember {
        derivedStateOf { sumMacros(filteredEntries) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(logBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Food Log",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = onExport,
                        colors = ButtonDefaults.buttonColors(containerColor = terracotta),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TogglePill(
                        label = "Daily",
                        selected = logWindow == LogWindow.Daily,
                        modifier = Modifier.weight(1f)
                    ) { onLogWindowChange(LogWindow.Daily) }
                    TogglePill(
                        label = "Weekly",
                        selected = logWindow == LogWindow.Weekly,
                        modifier = Modifier.weight(1f)
                    ) { onLogWindowChange(LogWindow.Weekly) }
                }
            }

            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                onSelectedDateChange(
                                    when (logWindow) {
                                    LogWindow.Daily -> selectedDate.minusDays(1)
                                    LogWindow.Weekly -> selectedDate.minusWeeks(1)
                                    }
                                )
                            }
                        ) {
                            Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Previous")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (logWindow) {
                                    LogWindow.Daily -> selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
                                    LogWindow.Weekly -> {
                                        val start = selectedDate.minusDays(selectedDate.dayOfWeek.value.toLong() - 1L)
                                        val end = start.plusDays(6)
                                        "${start.format(DateTimeFormatter.ofPattern("MMM d"))} - ${end.format(DateTimeFormatter.ofPattern("MMM d"))}"
                                    }
                                },
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(
                            onClick = {
                                onSelectedDateChange(
                                    when (logWindow) {
                                    LogWindow.Daily -> selectedDate.plusDays(1)
                                    LogWindow.Weekly -> selectedDate.plusWeeks(1)
                                    }
                                )
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Next")
                        }
                    }
                }
            }

            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            if (logWindow == LogWindow.Daily) "Today's Summary" else "Weekly Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(elevatedSurface)
                                    .padding(horizontal = 18.dp, vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${summary.calories}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = calorieTerracotta
                                    )
                                    Text("Total Calories", color = calorieTerracotta)
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                SummaryLine("Protein", "${summary.proteinGrams.pretty()}g", proteinBlue)
                                SummaryLine("Carbs", "${summary.carbsGrams.pretty()}g", carbsTeal)
                                SummaryLine("Fat", "${summary.fatGrams.pretty()}g", fatPurple)
                            }
                        }
                    }
                }
            }

            if (pendingApprovals.isNotEmpty()) {
                item {
                    Text(
                        "Pending Approval (${pendingApprovals.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                items(pendingApprovals, key = { it.id }) { pending ->
                    PendingApprovalCard(
                        pending = pending,
                        onApprove = { candidate -> onApprovePending(pending, candidate) },
                        onDismiss = { onDismissPending(pending) }
                    )
                }
            }

            item {
                Text(
                    "Meals (${filteredEntries.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            if (filteredEntries.isEmpty()) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("No meals in this window yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Log from the Voice or Scanner tab and it will show up here.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredEntries, key = { it.id }) { entry ->
                    MealLogCard(entry = entry, onDelete = { onDeleteEntry(entry) })
                }
            }
        }
    }
}

private data class NutrientGoal(
    val label: String,
    val current: Double,
    val target: Double,
    val unit: String
)

@Suppress("NewApi")
@Composable
private fun NutritionScreen(
    entries: List<FoodLogEntry>,
    selectedDate: LocalDate,
    onSelectedDateChange: (LocalDate) -> Unit
) {
    val dailyEntries by remember(selectedDate) {
        derivedStateOf {
            entries.filter { it.timestampUtcMillis.toLocalDate() == selectedDate }
        }
    }
    val nutrition by remember {
        derivedStateOf { sumNutrition(dailyEntries) }
    }
    val goals by remember {
        derivedStateOf { dailyNutritionGoals(nutrition) }
    }
    val coverageCount by remember {
        derivedStateOf { goals.count { it.current >= it.target } }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(logBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Daily Nutrition",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            }

            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onSelectedDateChange(selectedDate.minusDays(1)) }) {
                            Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Previous", tint = textPrimary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = spotifyGreen)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                                color = textPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(onClick = { onSelectedDateChange(selectedDate.plusDays(1)) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Next", tint = textPrimary)
                        }
                    }
                }
            }

            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Coverage",
                            color = textSecondary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "$coverageCount / ${goals.size}",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Goals reached", color = textSecondary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${dailyEntries.size}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = accentBright,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Meals counted", color = textSecondary)
                            }
                        }
                        Text(
                            "Percentages are approximate and based on the nutrient data available in your logged foods.",
                            color = textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (dailyEntries.isEmpty()) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("No logs for this day", style = MaterialTheme.typography.titleMedium, color = textPrimary)
                            Text(
                                "Log meals first and this page will estimate your daily nutrient coverage.",
                                color = textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(goals, key = { it.label }) { goal ->
                    NutrientProgressCard(goal)
                }
            }
        }
    }
}

@Composable
private fun ProductReviewCard(review: ProductReview, onLog: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                review.product.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            review.product.brand?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (review.blockedIngredients.isEmpty()) {
                AssistChip(
                    onClick = {},
                    label = { Text("Cleared against avoid list") },
                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Matched avoided ingredients",
                        color = softRed,
                        fontWeight = FontWeight.SemiBold
                    )
                    review.blockedIngredients.forEach { ingredient ->
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text(ingredient.label, color = softRed) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (ingredient.section) {
                                    IngredientSection.Allergens -> paleAmber
                                    else -> blush
                                }
                            )
                        )
                    }
                }
            }

            review.product.ingredientsText?.let { ingredients ->
                Text(
                    ingredients,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            review.product.macrosPer100g?.let { macros ->
                HorizontalDivider()
                MacroRow(macros)
            }

            if (review.blockedIngredients.isEmpty() && review.product.macrosPer100g != null) {
                Button(
                    onClick = onLog,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = forestGreen)
                ) {
                    Text("Log as meal")
                }
            }
        }
    }
}

@Composable
private fun PendingApprovalCard(
    pending: PendingMealApproval,
    onApprove: (MealCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    val primaryCandidate = remember(pending.id, pending.candidates) {
        pending.candidates.maxByOrNull { it.confidence }
    }
    val composition = remember(pending.id, pending.candidates) {
        primaryCandidate?.mealComposition
            ?: pending.candidates.firstNotNullOfOrNull { it.mealComposition }
    }
    val resolvedMacros = remember(pending.id, pending.candidates) {
        resolvedMacros(pending.candidates, composition)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = darkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("Pending approval") },
                    colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                        containerColor = paleAmber,
                        labelColor = darkAmber
                    )
                )
                Text(
                    pending.sourceDevice.replaceFirstChar { it.uppercase() },
                    color = accentBright,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                pending.originalQuery,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary
            )
            Text(
                "Captured at ${pending.timestampUtcMillis.toDisplayTime()}",
                style = MaterialTheme.typography.bodySmall,
                color = textSecondary
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(sageGreen)
                    .padding(14.dp)
            ) {
                Text(
                    "This meal was resolved into foods and linked ingredients. Review the structure before logging.",
                    color = mintTint,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            composition?.let { meal ->
                MealCompositionCard(
                    composition = meal,
                    mealMacros = resolvedMacros
                )
            } ?: run {
                Text(
                    "No structured meal composition is available for this older pending entry.",
                    color = textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val candidate = primaryCandidate ?: return@Button
                        onApprove(candidate)
                    },
                    enabled = primaryCandidate != null && resolvedMacros != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = forestGreen)
                ) {
                    Text("Approve and Log")
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = blush, contentColor = softRed)
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun MealCompositionCard(
    composition: MealComposition,
    mealMacros: MacroEstimate?,
    onLogFood: (ResolvedFoodItem) -> Unit = {},
    onLogIngredient: (ResolvedIngredient) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MealSummaryCard(composition = composition, mealMacros = mealMacros)
        composition.foods.forEach { food ->
            ResolvedFoodCard(
                food = food,
                onLogFood = { onLogFood(food) },
                onLogIngredient = onLogIngredient
            )
        }
    }
}

@Composable
private fun MealSummaryCard(
    composition: MealComposition,
    mealMacros: MacroEstimate?
) {
    val confidenceColor = confidenceColor(composition.overallConfidence)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = elevatedSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Meal Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "${composition.estimatedCalories ?: 0} kcal",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = calorieAccent
                    )
                    Text(
                        if (composition.usedExternalLookup) {
                            "Includes structured external fallback"
                        } else {
                            "Resolved fully on device"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary
                    )
                }
                ConfidenceBadge(
                    label = "Overall ${composition.overallConfidence}%",
                    confidence = composition.overallConfidence
                )
            }
            mealMacros?.let { macros ->
                MacroRow(macros)
            }
            if (mealMacros == null) {
                Text(
                    "Macro breakdown unavailable for this meal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = confidenceColor
                )
            }
        }
    }
}

@Composable
private fun ResolvedFoodCard(
    food: ResolvedFoodItem,
    onLogFood: () -> Unit,
    onLogIngredient: (ResolvedIngredient) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = darkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        buildString {
                            append(food.label)
                            food.quantityText?.takeIf { it.isNotBlank() }?.let {
                                append(" (")
                                append(it)
                                append(")")
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                    food.brand?.let {
                        Text(
                            it.replaceFirstChar { c -> c.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecondary
                        )
                    }
                    Text(
                        "Match: ${food.matchType.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = textTertiary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${food.estimatedCalories ?: 0} kcal",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = calorieAccent
                    )
                    ConfidenceBadge(
                        label = "${food.confidence}%",
                        confidence = food.confidence
                    )
                }
            }
            if (food.ingredients.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Ingredients",
                        style = MaterialTheme.typography.bodySmall,
                        color = mintTint,
                        fontWeight = FontWeight.SemiBold
                    )
                    food.ingredients.forEach { ingredient ->
                        ResolvedIngredientRow(
                            ingredient = ingredient,
                            onLogIngredient = { onLogIngredient(ingredient) }
                        )
                    }
                }
            } else {
                Text(
                    "No linked ingredients detected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary
                )
            }
            Button(
                onClick = onLogFood,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = forestGreen)
            ) {
                Text("Log Food Item")
            }
        }
    }
}

@Composable
private fun ResolvedIngredientRow(
    ingredient: ResolvedIngredient,
    onLogIngredient: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(sageGreen)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    ingredient.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textPrimary
                )
                ingredient.quantityText?.let { qty ->
                    Text(
                        qty,
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConfidenceBadge(
                    label = "${ingredient.confidence}%",
                    confidence = ingredient.confidence
                )
                Button(
                    onClick = onLogIngredient,
                    colors = ButtonDefaults.buttonColors(containerColor = terracotta),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Log")
                }
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(
    label: String,
    confidence: Int
) {
    val tone = confidenceColor(confidence)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color = tone,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun IngredientCheckboxRow(
    ingredient: IngredientRule,
    selected: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = onCheckedChange
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(ingredient.label, fontWeight = FontWeight.Medium)
            Text(
                ingredient.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MealLogCard(entry: FoodLogEntry, onDelete: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                    Text(
                        entry.timestampUtcMillis.toDisplayTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete log", tint = textSecondary)
                    }
                    Text(
                        "${entry.macros.calories}",
                        color = calorieAccent,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("kcal", style = MaterialTheme.typography.bodySmall, color = textSecondary)
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MacroColumn("Protein", "${entry.macros.proteinGrams.pretty()}g", proteinBlue)
                MacroColumn("Carbs", "${entry.macros.carbsGrams.pretty()}g", carbsTeal)
                MacroColumn("Fat", "${entry.macros.fatGrams.pretty()}g", fatPurple)
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = textSecondary)
        Text(value, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TogglePill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) spotifyGreen else elevatedSurface,
            contentColor = if (selected) nearBlackGreen else textPrimary
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (selected) 4.dp else 0.dp)
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MacroRow(macros: MacroEstimate) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        MacroStat("Protein", "${macros.proteinGrams.pretty()} g", proteinBlue)
        MacroStat("Carbs", "${macros.carbsGrams.pretty()} g", carbsTeal)
        MacroStat("Fat", "${macros.fatGrams.pretty()} g", fatPurple)
    }
}

@Composable
private fun MacroStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = color)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = textSecondary
        )
    }
}

@Composable
private fun MacroColumn(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = textSecondary)
        Text(value, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AppCard(
    modifier: Modifier = Modifier,
    innerPadding: androidx.compose.ui.unit.Dp = 18.dp,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = darkCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}

private fun launchSpeechInput(context: Context, launcher: ActivityResultLauncher<Intent>) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your meal")
    }
    try {
        launcher.launch(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "Speech recognition is not available on this device.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun blockedIngredients(
    ingredientsText: String?,
    selectedKeys: Set<String>,
    ingredientRules: List<IngredientRule>
): List<IngredientRule> {
    if (ingredientsText.isNullOrBlank()) return emptyList()
    val haystack = ingredientsText.lowercase(Locale.US)
    return ingredientRules.filter { ingredient ->
        ingredient.key in selectedKeys && ingredient.aliases.any { alias ->
            alias.lowercase(Locale.US) in haystack
        }
    }
}

private fun sectionTitle(section: IngredientSection): String =
    when (section) {
        IngredientSection.Common -> "Common"
        IngredientSection.Additives -> "Additives and flavorings"
        IngredientSection.Sweeteners -> "Sweeteners and syrups"
        IngredientSection.Oils -> "Refined oils"
        IngredientSection.Preservatives -> "Preservatives"
        IngredientSection.Dyes -> "Artificial colors"
        IngredientSection.Allergens -> "Allergens"
    }

private fun sumMacros(entries: List<FoodLogEntry>): MacroEstimate =
    entries.fold(MacroEstimate(0, 0.0, 0.0, 0.0)) { acc, entry ->
        MacroEstimate(
            calories = acc.calories + entry.macros.calories,
            proteinGrams = acc.proteinGrams + entry.macros.proteinGrams,
            carbsGrams = acc.carbsGrams + entry.macros.carbsGrams,
            fatGrams = acc.fatGrams + entry.macros.fatGrams
        )
    }

private fun sumNutrition(entries: List<FoodLogEntry>): NutritionEstimate =
    entries.fold(NutritionEstimate()) { acc, entry ->
        val nutrition = entry.nutrition ?: return@fold acc
        NutritionEstimate(
            fiberGrams = acc.fiberGrams + nutrition.fiberGrams,
            sugarGrams = acc.sugarGrams + nutrition.sugarGrams,
            sodiumMg = acc.sodiumMg + nutrition.sodiumMg,
            potassiumMg = acc.potassiumMg + nutrition.potassiumMg,
            calciumMg = acc.calciumMg + nutrition.calciumMg,
            ironMg = acc.ironMg + nutrition.ironMg,
            vitaminCMg = acc.vitaminCMg + nutrition.vitaminCMg,
            vitaminDMcg = acc.vitaminDMcg + nutrition.vitaminDMcg,
            vitaminAMcg = acc.vitaminAMcg + nutrition.vitaminAMcg,
            vitaminB12Mcg = acc.vitaminB12Mcg + nutrition.vitaminB12Mcg
        )
    }

private fun dailyNutritionGoals(nutrition: NutritionEstimate): List<NutrientGoal> = listOf(
    NutrientGoal("Fiber", nutrition.fiberGrams, 28.0, "g"),
    NutrientGoal("Sugar", nutrition.sugarGrams, 50.0, "g"),
    NutrientGoal("Sodium", nutrition.sodiumMg, 2300.0, "mg"),
    NutrientGoal("Potassium", nutrition.potassiumMg, 4700.0, "mg"),
    NutrientGoal("Calcium", nutrition.calciumMg, 1300.0, "mg"),
    NutrientGoal("Iron", nutrition.ironMg, 18.0, "mg"),
    NutrientGoal("Vitamin C", nutrition.vitaminCMg, 90.0, "mg"),
    NutrientGoal("Vitamin D", nutrition.vitaminDMcg, 20.0, "mcg"),
    NutrientGoal("Vitamin A", nutrition.vitaminAMcg, 900.0, "mcg"),
    NutrientGoal("Vitamin B12", nutrition.vitaminB12Mcg, 2.4, "mcg")
)

@Composable
private fun NutrientProgressCard(goal: NutrientGoal) {
    val ratio = if (goal.target == 0.0) 0f else (goal.current / goal.target).toFloat().coerceIn(0f, 1f)
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(goal.label, color = textPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    "${(ratio * 100).toInt()}%",
                    color = accentBright,
                    fontWeight = FontWeight.Bold
                )
            }
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = spotifyGreen,
                trackColor = elevatedSurface
            )
            Text(
                "${goal.current.pretty()} ${goal.unit} / ${goal.target.pretty()} ${goal.unit}",
                color = textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Suppress("NewApi")
private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

@Suppress("NewApi")
private fun Long.toDisplayTime(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("hh:mm a"))

private fun Double.pretty(): String =
    if (this % 1.0 == 0.0) toInt().toString() else String.format(Locale.US, "%.1f", this)

private fun compositionTitle(composition: MealComposition?, fallback: String): String {
    if (composition == null) return fallback
    val foodNames = composition.foods.map { food ->
        buildString {
            append(food.label)
            food.quantityText?.takeIf { it.isNotBlank() }?.let {
                append(" (")
                append(it)
                append(")")
            }
        }
    }
    val standaloneIngredientNames = composition.ingredients
        .filter { it.parentFoodId == null }
        .map { it.label }

    val names = (foodNames + standaloneIngredientNames).take(3).joinToString(" + ")
    return if (names.isBlank()) fallback else names
}

private fun compositionSource(composition: MealComposition?, fallback: String): String =
    when {
        composition == null -> fallback
        composition.usedExternalLookup -> "Food Resolution Engine (Structured OFF/USDA fallback)"
        else -> "Food Resolution Engine (Offline)"
    }

private fun resolvedMacros(
    candidates: List<MealCandidate>,
    composition: MealComposition?
): MacroEstimate? {
    if (composition == null) return candidates.firstOrNull()?.product?.macrosPer100g
    
    val foodMacros = composition.foods.mapNotNull { food ->
        candidates.firstOrNull { candidate ->
            candidate.product.name.equals(food.label, ignoreCase = true) &&
                candidate.product.macrosPer100g != null
        }?.product?.macrosPer100g?.let { macros ->
            val mult = food.quantityMultiplier
            MacroEstimate(
                calories = (macros.calories * mult).toInt(),
                proteinGrams = macros.proteinGrams * mult,
                carbsGrams = macros.carbsGrams * mult,
                fatGrams = macros.fatGrams * mult
            )
        }
    }

    val standaloneIngredientMacros = composition.ingredients
        .filter { it.parentFoodId == null }
        .mapNotNull { ingredient ->
            ingredient.estimatedCalories?.let { cal ->
                MacroEstimate(calories = cal, proteinGrams = 0.0, carbsGrams = 0.0, fatGrams = 0.0)
            }
        }

    val allResolved = foodMacros + standaloneIngredientMacros

    if (allResolved.isNotEmpty()) {
        return MacroEstimate(
            calories = composition.estimatedCalories ?: allResolved.sumOf { it.calories },
            proteinGrams = allResolved.sumOf { it.proteinGrams },
            carbsGrams = allResolved.sumOf { it.carbsGrams },
            fatGrams = allResolved.sumOf { it.fatGrams }
        )
    }

    val firstMacro = candidates.firstOrNull { it.product.macrosPer100g != null }?.product?.macrosPer100g
    return firstMacro?.copy(
        calories = composition.estimatedCalories ?: firstMacro.calories
    ) ?: composition.estimatedCalories?.let {
        MacroEstimate(
            calories = it,
            proteinGrams = 0.0,
            carbsGrams = 0.0,
            fatGrams = 0.0
        )
    }
}

private fun confidenceColor(confidence: Int): Color =
    when {
        confidence >= 80 -> accentBright
        confidence >= 55 -> darkAmber
        else -> softRed
    }

private fun shareExport(activity: Activity, exportFile: File) {
    val uri: Uri = FileProvider.getUriForFile(
        activity,
        "${activity.packageName}.fileprovider",
        exportFile
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    activity.startActivity(Intent.createChooser(intent, "Export ComFood data"))
}

private fun resolvedNutrition(
    candidates: List<MealCandidate>,
    composition: MealComposition?
): NutritionEstimate? {
    if (composition == null) return candidates.maxByOrNull { it.confidence }?.product?.nutritionPerServing
    
    val foodNutritions = composition.foods.mapNotNull { food ->
        candidates.firstOrNull { candidate ->
            candidate.product.name.equals(food.label, ignoreCase = true) &&
                candidate.product.nutritionPerServing != null
        }?.product?.nutritionPerServing?.let { nutrition ->
            val mult = food.quantityMultiplier
            NutritionEstimate(
                fiberGrams = nutrition.fiberGrams * mult,
                sugarGrams = nutrition.sugarGrams * mult,
                sodiumMg = nutrition.sodiumMg * mult,
                potassiumMg = nutrition.potassiumMg * mult,
                calciumMg = nutrition.calciumMg * mult,
                ironMg = nutrition.ironMg * mult,
                vitaminCMg = nutrition.vitaminCMg * mult,
                vitaminDMcg = nutrition.vitaminDMcg * mult,
                vitaminAMcg = nutrition.vitaminAMcg * mult,
                vitaminB12Mcg = nutrition.vitaminB12Mcg * mult
            )
        }
    }

    val standaloneNutritions = composition.ingredients
        .filter { it.parentFoodId == null }
        .mapNotNull { it.estimatedNutrition }

    val allResolved = foodNutritions + standaloneNutritions

    if (allResolved.isEmpty()) return candidates.maxByOrNull { it.confidence }?.product?.nutritionPerServing

    return allResolved.fold(NutritionEstimate()) { acc, n ->
        NutritionEstimate(
            fiberGrams = acc.fiberGrams + n.fiberGrams,
            sugarGrams = acc.sugarGrams + n.sugarGrams,
            sodiumMg = acc.sodiumMg + n.sodiumMg,
            potassiumMg = acc.potassiumMg + n.potassiumMg,
            calciumMg = acc.calciumMg + n.calciumMg,
            ironMg = acc.ironMg + n.ironMg,
            vitaminCMg = acc.vitaminCMg + n.vitaminCMg,
            vitaminDMcg = acc.vitaminDMcg + n.vitaminDMcg,
            vitaminAMcg = acc.vitaminAMcg + n.vitaminAMcg,
            vitaminB12Mcg = acc.vitaminB12Mcg + n.vitaminB12Mcg
        )
    }
}

private fun resolvedFoodMacros(food: ResolvedFoodItem, candidates: List<MealCandidate>): MacroEstimate? {
    val base = candidates.firstOrNull { it.product.name.equals(food.label, ignoreCase = true) }?.product?.macrosPer100g
        ?: food.estimatedCalories?.let { MacroEstimate(it, 0.0, 0.0, 0.0) }
        ?: return null

    val mult = food.quantityMultiplier
    return MacroEstimate(
        calories = (base.calories * mult).toInt(),
        proteinGrams = base.proteinGrams * mult,
        carbsGrams = base.carbsGrams * mult,
        fatGrams = base.fatGrams * mult
    )
}

private fun resolvedFoodNutrition(food: ResolvedFoodItem, candidates: List<MealCandidate>): NutritionEstimate? {
    val base = candidates.firstOrNull { it.product.name.equals(food.label, ignoreCase = true) }?.product?.nutritionPerServing
        ?: food.estimatedNutrition
        ?: return null

    val mult = food.quantityMultiplier
    return NutritionEstimate(
        fiberGrams = base.fiberGrams * mult,
        sugarGrams = base.sugarGrams * mult,
        sodiumMg = base.sodiumMg * mult,
        potassiumMg = base.potassiumMg * mult,
        calciumMg = base.calciumMg * mult,
        ironMg = base.ironMg * mult,
        vitaminCMg = base.vitaminCMg * mult,
        vitaminDMcg = base.vitaminDMcg * mult,
        vitaminAMcg = base.vitaminAMcg * mult,
        vitaminB12Mcg = base.vitaminB12Mcg * mult
    )
}

private fun resolvedIngredientMacros(ingredient: ResolvedIngredient): MacroEstimate? {
    return ingredient.estimatedCalories?.let { MacroEstimate(it, 0.0, 0.0, 0.0) }
}

@Suppress("UNUSED_PARAMETER")
private fun ingredientLogTitle(ingredient: ResolvedIngredient, composition: MealComposition?): String {
    return ingredient.label
}

