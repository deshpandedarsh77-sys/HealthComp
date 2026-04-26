package com.example.healthcomp // ⚠️ REPLACE WITH YOUR PACKAGE NAME

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// --- 1. DATA LAYER (Room DB for Meals) ---
@Entity(tableName = "meals")
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val protein: Float, val carbs: Float, val fats: Float, val calories: Float,
    val date: Long = System.currentTimeMillis()
)

@Dao
interface MealDao {
    @Query("SELECT * FROM meals ORDER BY date DESC")
    fun getAllMeals(): Flow<List<Meal>>
    @Insert
    suspend fun insertMeal(meal: Meal)
}

@Database(entities = [Meal::class], version = 1)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    companion object {
        @Volatile private var INSTANCE: HealthDatabase? = null
        fun getDatabase(context: Context): HealthDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext, HealthDatabase::class.java, "healthcomp_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

data class DailyPlan(
    val muscles: List<String> = emptyList(),
    val exercises: List<String> = emptyList()
)

// --- 2. LOGIC LAYER (ViewModel) ---
class HealthViewModel(private val dao: MealDao) : ViewModel() {
    val allMeals: Flow<List<Meal>> = dao.getAllMeals()

    var userPoints by mutableStateOf(150)
        private set
    var isWorkoutCompleteToday by mutableStateOf(false)
        private set

    val weeklySplit = mutableStateMapOf(
        "Monday" to DailyPlan(listOf("Chest", "Triceps"), listOf("Bench Press", "Tricep Pushdowns", "Incline Dumbbell Press")),
        "Tuesday" to DailyPlan(listOf("Back", "Biceps"), listOf("Lat Pulldown", "Barbell Rows", "Bicep Curls")),
        "Wednesday" to DailyPlan(listOf("Legs"), listOf("Squats", "Leg Press", "Calf Raises")),
        "Thursday" to DailyPlan(listOf("Shoulders", "Abs"), listOf("Overhead Press", "Lateral Raises", "Crunches")),
        "Friday" to DailyPlan(listOf("Upper Body"), listOf("Pull-ups", "Dips", "Push-ups")),
        "Saturday" to DailyPlan(listOf("Legs"), listOf("Deadlifts", "Hamstring Curls")),
        "Sunday" to DailyPlan(emptyList(), emptyList())
    )

    // --- NEW: Date Selection State for "My Meals" ---
    var selectedDate by mutableStateOf(LocalDate.now())
        private set

    fun changeDate(daysToAdd: Long) {
        selectedDate = selectedDate.plusDays(daysToAdd)
    }

    // Reactively filter meals for the selected date
    val mealsForSelectedDate: Flow<List<Meal>> = combine(allMeals, snapshotFlow { selectedDate }) { meals, date ->
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        meals.filter { it.date in startOfDay..endOfDay }
    }

    fun completeWorkout() {
        if (!isWorkoutCompleteToday) {
            userPoints += 50
            isWorkoutCompleteToday = true
        }
    }
    fun addMuscle(day: String, muscle: String) {
        val currentPlan = weeklySplit[day] ?: DailyPlan()
        weeklySplit[day] = currentPlan.copy(muscles = currentPlan.muscles + muscle)
    }
    fun removeMuscle(day: String, muscle: String) {
        val currentPlan = weeklySplit[day] ?: DailyPlan()
        weeklySplit[day] = currentPlan.copy(muscles = currentPlan.muscles.filter { it != muscle })
    }
    fun addExercise(day: String, exercise: String) {
        val currentPlan = weeklySplit[day] ?: DailyPlan()
        weeklySplit[day] = currentPlan.copy(exercises = currentPlan.exercises + exercise)
    }
    fun removeExercise(day: String, exercise: String) {
        val currentPlan = weeklySplit[day] ?: DailyPlan()
        weeklySplit[day] = currentPlan.copy(exercises = currentPlan.exercises.filter { it != exercise })
    }
    fun logMeal(name: String, p: Float, c: Float, f: Float) {
        val totalCals = (p * 4) + (c * 4) + (f * 9)
        viewModelScope.launch {
            dao.insertMeal(Meal(name = name, protein = p, carbs = c, fats = f, calories = totalCals))
        }
    }
}

// --- 3. NAVIGATION DEFINITIONS ---
sealed class Screen(val route: String, val title: String, val icon: ImageVector?) {
    object Dashboard : Screen("dashboard", "Home", Icons.Default.Home)
    object MyMeals : Screen("my_meals", "Diary", Icons.Default.List) // NEW TAB
    object Calendar : Screen("calendar", "Calendar", Icons.Default.DateRange)
    object Social : Screen("social", "Social", Icons.Default.Person)
    object LogMeal : Screen("log_meal", "Log Meal", null)
}

// --- 4. APP SHELL & ROUTING ---
@Composable
fun HealthCompMainApp(viewModel: HealthViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Updated to include MyMeals in the bottom bar
    val showBottomBar = currentRoute in listOf(Screen.Dashboard.route, Screen.MyMeals.route, Screen.Calendar.route, Screen.Social.route)

    Scaffold(
        bottomBar = { if (showBottomBar) BottomNavigationBar(navController, currentRoute) }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = Screen.Dashboard.route, modifier = Modifier.padding(innerPadding)) {
            composable(Screen.Dashboard.route) { DashboardScreen(viewModel, navController) }
            composable(Screen.MyMeals.route) { MyMealsScreen(viewModel) } // NEW SCREEN
            composable(Screen.Calendar.route) { CalendarScreen(viewModel) }
            composable(Screen.Social.route) { SocialScreen(viewModel) }
            composable(Screen.LogMeal.route) { MealLoggingScreen(viewModel, navController) }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController, currentRoute: String?) {
    val items = listOf(Screen.Dashboard, Screen.MyMeals, Screen.Calendar, Screen.Social)
    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon!!, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

// --- 5. THE SCREENS ---

// (Dashboard, MealLogging, Calendar, Social remain largely the same, optimized for space)

@Composable
fun DashboardScreen(viewModel: HealthViewModel, navController: NavHostController) {
    val currentDay = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val todaysPlan = viewModel.weeklySplit[currentDay] ?: DailyPlan()
    val muscleGroupText = if (todaysPlan.muscles.isNotEmpty()) todaysPlan.muscles.joinToString(" & ") else "Rest Day"

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Good Morning!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Consistency Points: ${viewModel.userPoints}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(currentDay, style = MaterialTheme.typography.titleMedium)
                Text(muscleGroupText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (todaysPlan.exercises.isNotEmpty()) {
                    todaysPlan.exercises.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Button(
                    onClick = { viewModel.completeWorkout() }, modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isWorkoutCompleteToday && todaysPlan.muscles.isNotEmpty()
                ) { Text(if (viewModel.isWorkoutCompleteToday) "Completed! (+50 pts)" else "Mark Completed") }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Nutrition", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = { navController.navigate(Screen.LogMeal.route) }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Add Meal")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Log a Meal", fontSize = 18.sp)
        }
    }
}

// --- NEW MY MEALS SCREEN ---
@Composable
fun MyMealsScreen(viewModel: HealthViewModel) {
    val meals by viewModel.mealsForSelectedDate.collectAsState(initial = emptyList())

    // Calculate totals dynamically for the selected date
    val totalCals = meals.sumOf { it.calories.toDouble() }.toInt()
    val totalP = meals.sumOf { it.protein.toDouble() }.toInt()
    val totalC = meals.sumOf { it.carbs.toDouble() }.toInt()
    val totalF = meals.sumOf { it.fats.toDouble() }.toInt()

    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Date Navigator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.changeDate(-1) }) { Icon(Icons.Default.KeyboardArrowLeft, "Previous Day") }
            Text(
                text = if (viewModel.selectedDate == LocalDate.now()) "Today" else viewModel.selectedDate.format(dateFormatter),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { viewModel.changeDate(1) }) { Icon(Icons.Default.KeyboardArrowRight, "Next Day") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Macro Summary Card
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MacroStat("Cals", totalCals.toString())
                MacroStat("Pro", "${totalP}g")
                MacroStat("Carb", "${totalC}g")
                MacroStat("Fat", "${totalF}g")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // Meals List
        if (meals.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No meals logged for this day.", color = Color.Gray)
            }
        } else {
            LazyColumn {
                items(meals) { meal ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(meal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("P:${meal.protein}g C:${meal.carbs}g F:${meal.fats}g", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Text("${meal.calories.toInt()} kcal", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

// Helper composable for the macro summary
@Composable
fun MacroStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun MealLoggingScreen(viewModel: HealthViewModel, navController: NavHostController) {
    var mealName by remember { mutableStateOf("") }
    var pInput by remember { mutableStateOf("") }
    var cInput by remember { mutableStateOf("") }
    var fInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text("Log Nutrition", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = mealName, onValueChange = { mealName = it }, label = { Text("Meal Name") }, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = pInput, onValueChange = { pInput = it }, label = { Text("P") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = cInput, onValueChange = { cInput = it }, label = { Text("C") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = fInput, onValueChange = { fInput = it }, label = { Text("F") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        Button(
            onClick = {
                if (mealName.isNotBlank()) {
                    viewModel.logMeal(mealName, pInput.toFloatOrNull() ?: 0f, cInput.toFloatOrNull() ?: 0f, fInput.toFloatOrNull() ?: 0f)
                    Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save Meal") }
    }
}

@Composable
fun CalendarScreen(viewModel: HealthViewModel) {
    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Weekly Split", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(daysOfWeek) { day ->
                val plan = viewModel.weeklySplit[day] ?: DailyPlan()
                var expanded by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { expanded = !expanded },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(day, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Expand")
                        }
                        val muscleText = if (plan.muscles.isNotEmpty()) plan.muscles.joinToString(" • ") else "Rest Day"
                        Text(muscleText, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)

                        AnimatedVisibility(visible = expanded) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(8.dp))

                                Text("Target Muscles:", style = MaterialTheme.typography.labelLarge)
                                if (plan.muscles.isEmpty()) { Text("No muscles added.", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                                else {
                                    plan.muscles.forEach { muscle ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("• $muscle", style = MaterialTheme.typography.bodyMedium)
                                            IconButton(onClick = { viewModel.removeMuscle(day, muscle) }, modifier = Modifier.size(20.dp)) {
                                                Icon(Icons.Default.Clear, contentDescription = "Remove Muscle", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Exercises:", style = MaterialTheme.typography.labelLarge)
                                if (plan.exercises.isEmpty()) { Text("No exercises added.", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                                else {
                                    plan.exercises.forEach { exercise ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("• $exercise", style = MaterialTheme.typography.bodyMedium)
                                            IconButton(onClick = { viewModel.removeExercise(day, exercise) }, modifier = Modifier.size(20.dp)) {
                                                Icon(Icons.Default.Clear, contentDescription = "Remove Exercise", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                var newMuscle by remember { mutableStateOf("") }
                                var newExercise by remember { mutableStateOf("") }
                                OutlinedTextField(value = newMuscle, onValueChange = { newMuscle = it }, label = { Text("Add Muscle (e.g. Back)") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { if(newMuscle.isNotBlank()) { viewModel.addMuscle(day, newMuscle); newMuscle = "" } }) { Icon(Icons.Default.Add, "Add") } })
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(value = newExercise, onValueChange = { newExercise = it }, label = { Text("Add Exercise (e.g. Deadlift)") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { IconButton(onClick = { if(newExercise.isNotBlank()) { viewModel.addExercise(day, newExercise); newExercise = "" } }) { Icon(Icons.Default.Add, "Add") } })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SocialScreen(viewModel: HealthViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Leaderboard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Waiting for backend sync...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("You", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${viewModel.userPoints} pts", style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Om Shinde", style = MaterialTheme.typography.titleLarge)
                Text("120 pts", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

// --- 6. ENTRY POINT ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = HealthDatabase.getDatabase(applicationContext)
        val viewModel = HealthViewModel(db.mealDao())

        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    HealthCompMainApp(viewModel)
                }
            }
        }
    }
}