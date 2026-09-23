package dev.jason.gboardpatches.extension.unitto.compose

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * In-overlay Jetpack Compose Unitto surface.
 *
 * Java entry: [createView]
 * Tabs: Calculator · Converter · Date
 */
object UnittoComposeContent {
    @JvmStatic
    fun createView(context: Context): View {
        return ComposeView(context).apply {
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    UnittoHubScreen(context)
                }
            }
        }
    }
}

private enum class HubTab(val label: String) {
    Calculator("Calculator"),
    Converter("Converter"),
    Date("Date"),
}

@Composable
fun UnittoHubScreen(context: Context) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = HubTab.entries

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        ScrollableTabRow(
            selectedTabIndex = tabIndex,
            containerColor = Color(0xFF1C1C1E),
            contentColor = Color.White,
            edgePadding = 8.dp
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(tab.label) }
                )
            }
        }
        when (tabs[tabIndex]) {
            HubTab.Calculator -> UnittoCalculatorScreen(context, Modifier.weight(1f))
            HubTab.Converter -> UnittoConverterScreen(context, Modifier.weight(1f))
            HubTab.Date -> UnittoDateScreen(context, Modifier.weight(1f))
        }
    }
}

@Composable
fun UnittoCalculatorScreen(context: Context, modifier: Modifier = Modifier) {
    var expression by remember {
        mutableStateOf(
            try {
                dev.jason.gboardpatches.extension.unitto.UnittoWebCache.lastExpression(context)
            } catch (_: Throwable) {
                ""
            }
        )
    }
    var result by remember {
        mutableStateOf(
            try {
                dev.jason.gboardpatches.extension.unitto.UnittoWebCache.lastResult(context)
            } catch (_: Throwable) {
                ""
            }
        )
    }

    fun eval(expr: String): String = evaluateExpression(expr)

    fun persist() {
        try {
            dev.jason.gboardpatches.extension.unitto.UnittoWebCache
                .saveCalculatorState(context, expression, result)
        } catch (_: Throwable) {
        }
    }

    fun onKey(key: String) {
        when (key) {
            "C" -> {
                expression = ""
                result = ""
            }
            "⌫" -> if (expression.isNotEmpty()) expression = expression.dropLast(1)
            "=" -> {
                val value = eval(expression)
                result = value
                if (value.isNotEmpty() && value != "Error") expression = value
            }
            else -> expression += key
        }
        if (key != "=" && expression.isNotEmpty()) {
            val preview = eval(expression)
            if (preview.isNotEmpty() && preview != "Error") result = "= $preview"
        }
        persist()
    }

    fun insertIntoField() {
        val text = when {
            result.isNotEmpty() && !result.startsWith("=") && result != "Error" -> result
            result.startsWith("=") -> result.removePrefix("=").trim()
            expression.isNotEmpty() -> {
                val v = eval(expression)
                if (v.isNotEmpty() && v != "Error") v else expression
            }
            else -> ""
        }
        if (text.isEmpty()) {
            Toast.makeText(context, "Nothing to insert", Toast.LENGTH_SHORT).show()
            return
        }
        val ok = try {
            val bridge = Class.forName(
                "dev.jason.gboardpatches.extension.overlay.InputConnectionBridge"
            )
            val method = bridge.getMethod("commitText", CharSequence::class.java)
            method.invoke(null, text) as Boolean
        } catch (_: Throwable) {
            false
        }
        Toast.makeText(
            context,
            if (ok) "Inserted" else "No focused field",
            Toast.LENGTH_SHORT
        ).show()
    }

    val rows = listOf(
        listOf("C", "(", ")", "/"),
        listOf("7", "8", "9", "*"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "⌫", "="),
        listOf("sin(", "cos(", "sqrt(", "^"),
    )

    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = expression.ifEmpty { "0" },
            color = Color.White,
            fontSize = 26.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        )
        Text(
            text = result,
            color = Color(0xFF8E8E93),
            fontSize = 15.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    val isOp = key in listOf("/", "*", "-", "+", "=", "C", "⌫") ||
                        key.endsWith("(") || key == "^"
                    Button(
                        onClick = { onKey(key) },
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOp) Color(0xFF0A84FF) else Color(0xFF2C2C2E),
                            contentColor = Color.White
                        )
                    ) {
                        val label = if (key.endsWith("(") && key.length > 2) key.dropLast(1) else key
                        Text(label, fontSize = if (key.length > 3) 12.sp else 16.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Button(
            onClick = { insertIntoField() },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF30D158),
                contentColor = Color.Black
            )
        ) {
            Text("Insert into field", fontSize = 14.sp)
        }
    }
}

@Composable
fun UnittoConverterScreen(context: Context, modifier: Modifier = Modifier) {
    var amount by remember {
        mutableStateOf(
            try {
                dev.jason.gboardpatches.extension.unitto.UnittoWebCache.convAmount(context)
            } catch (_: Throwable) {
                "1"
            }
        )
    }
    var fromUnit by remember {
        mutableStateOf(
            try {
                dev.jason.gboardpatches.extension.unitto.UnittoWebCache.convFrom(context)
            } catch (_: Throwable) {
                "km"
            }
        )
    }
    var toUnit by remember {
        mutableStateOf(
            try {
                dev.jason.gboardpatches.extension.unitto.UnittoWebCache.convTo(context)
            } catch (_: Throwable) {
                "mi"
            }
        )
    }
    var output by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Length") }

    val lengthFactors = mapOf(
        "mm" to 0.001, "cm" to 0.01, "m" to 1.0, "km" to 1000.0,
        "in" to 0.0254, "ft" to 0.3048, "yd" to 0.9144, "mi" to 1609.344,
    )
    val massFactors = mapOf(
        "mg" to 0.001, "g" to 1.0, "kg" to 1000.0,
        "oz" to 28.3495, "lb" to 453.592, "t" to 1_000_000.0,
    )
    val factors = if (category == "Mass") massFactors else lengthFactors
    val units = factors.keys.toList()

    fun convert() {
        val value = amount.toDoubleOrNull()
        output = if (category == "Temp") {
            convertTemperature(value, fromUnit, toUnit)
        } else {
            val from = factors[fromUnit]
            val to = factors[toUnit]
            if (value == null || from == null || to == null) "Error"
            else formatNumber(value * from / to)
        }
        try {
            dev.jason.gboardpatches.extension.unitto.UnittoWebCache
                .saveConverterState(context, amount, fromUnit, toUnit)
        } catch (_: Throwable) {
        }
    }

    Column(modifier = modifier.padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Length", "Mass", "Temp").forEach { cat ->
                val active = category == cat
                Button(
                    onClick = {
                        category = cat
                        when (cat) {
                            "Mass" -> {
                                fromUnit = "kg"; toUnit = "lb"
                            }
                            "Temp" -> {
                                fromUnit = "C"; toUnit = "F"
                            }
                            else -> {
                                fromUnit = "km"; toUnit = "mi"
                            }
                        }
                        convert()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (active) Color(0xFF0A84FF) else Color(0xFF2C2C2E),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) { Text(cat, fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextField(
            value = amount,
            onValueChange = { amount = it; convert() },
            label = { Text("Value") },
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("From: $fromUnit", color = Color(0xFFAEAEB2), fontSize = 13.sp)
        UnitChipRow(
            if (category == "Temp") listOf("C", "F", "K") else units,
            fromUnit
        ) { fromUnit = it; convert() }
        Spacer(Modifier.height(8.dp))
        Text("To: $toUnit", color = Color(0xFFAEAEB2), fontSize = 13.sp)
        UnitChipRow(
            if (category == "Temp") listOf("C", "F", "K") else units,
            toUnit
        ) { toUnit = it; convert() }
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (output.isEmpty()) "—" else output,
            color = Color.White,
            fontSize = 28.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = if (output.isNotEmpty() && output != "Error") {
                "$amount $fromUnit = $output $toUnit"
            } else {
                ""
            },
            color = Color(0xFF8E8E93),
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val text = if (output.isNotEmpty() && output != "Error") output else ""
                if (text.isEmpty()) {
                    Toast.makeText(context, "Nothing to insert", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val ok = try {
                    val bridge = Class.forName(
                        "dev.jason.gboardpatches.extension.overlay.InputConnectionBridge"
                    )
                    val method = bridge.getMethod("commitText", CharSequence::class.java)
                    method.invoke(null, text) as Boolean
                } catch (_: Throwable) {
                    false
                }
                Toast.makeText(
                    context,
                    if (ok) "Inserted" else "No focused field",
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF30D158),
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Insert result into field")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Offline Length / Mass / Temp · full Unitto web has 600+ units & currency",
            color = Color(0xFF636366),
            fontSize = 11.sp
        )
    }
}

@Composable
fun UnittoDateScreen(context: Context, modifier: Modifier = Modifier) {
    var days by remember {
        mutableStateOf(
            try {
                dev.jason.gboardpatches.extension.unitto.UnittoWebCache.dateDays(context)
            } catch (_: Throwable) {
                "7"
            }
        )
    }
    var result by remember { mutableStateOf("") }

    fun compute() {
        val n = days.toLongOrNull()
        result = if (n == null) {
            "Error"
        } else {
            val ms = System.currentTimeMillis() + n * 24L * 60L * 60L * 1000L
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = ms
            "%04d-%02d-%02d".format(
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            )
        }
        try {
            dev.jason.gboardpatches.extension.unitto.UnittoWebCache.saveDateDays(context, days)
        } catch (_: Throwable) {
        }
    }

    Column(modifier = modifier.padding(12.dp)) {
        Text("Date offset", color = Color.White, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        TextField(
            value = days,
            onValueChange = { days = it; compute() },
            label = { Text("Days from today (+/-)") },
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (result.isEmpty()) "—" else result,
            color = Color.White,
            fontSize = 28.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (result.isEmpty() || result == "Error") {
                    Toast.makeText(context, "Nothing to insert", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val ok = try {
                    val bridge = Class.forName(
                        "dev.jason.gboardpatches.extension.overlay.InputConnectionBridge"
                    )
                    val method = bridge.getMethod("commitText", CharSequence::class.java)
                    method.invoke(null, result) as Boolean
                } catch (_: Throwable) {
                    false
                }
                Toast.makeText(
                    context,
                    if (ok) "Inserted" else "No focused field",
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF30D158),
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Insert date into field")
        }
        Text(
            text = "Offline day offset · full Unitto date calculator via App / Web",
            color = Color(0xFF636366),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun UnitChipRow(
    units: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        units.forEach { u ->
            val active = u == selected
            Button(
                onClick = { onSelect(u) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (active) Color(0xFF0A84FF) else Color(0xFF2C2C2E),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(u, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = Color(0xFF2C2C2E),
    unfocusedContainerColor = Color(0xFF2C2C2E),
    focusedIndicatorColor = Color(0xFF0A84FF),
    unfocusedIndicatorColor = Color(0xFF3A3A3C),
    focusedLabelColor = Color(0xFFAEAEB2),
    unfocusedLabelColor = Color(0xFF8E8E93),
    cursorColor = Color.White
)

private fun evaluateExpression(expr: String): String {
    return try {
        val clazz = Class.forName(
            "dev.jason.gboardpatches.extension.calculator.GboardCalculatorEngine"
        )
        val method = clazz.getMethod("evaluate", String::class.java)
        (method.invoke(null, expr) as? String) ?: ""
    } catch (_: Throwable) {
        ""
    }
}

private fun convertTemperature(value: Double?, from: String, to: String): String {
    if (value == null) return "Error"
    val celsius = when (from) {
        "C" -> value
        "F" -> (value - 32.0) * 5.0 / 9.0
        "K" -> value - 273.15
        else -> return "Error"
    }
    val out = when (to) {
        "C" -> celsius
        "F" -> celsius * 9.0 / 5.0 + 32.0
        "K" -> celsius + 273.15
        else -> return "Error"
    }
    return formatNumber(out)
}

private fun formatNumber(value: Double): String {
    if (value == value.toLong().toDouble()) {
        return value.toLong().toString()
    }
    return String.format(java.util.Locale.US, "%.6g", value)
}
