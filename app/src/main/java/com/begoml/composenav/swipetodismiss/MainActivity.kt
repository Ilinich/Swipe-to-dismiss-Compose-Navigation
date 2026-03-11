package com.begoml.composenav.swipetodismiss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.begoml.composenav.swipetodismiss.swipe.SwipeToDismissSceneStrategy
import com.begoml.composenav.swipetodismiss.swipe.swipeToDismissHorizontalEntry
import com.begoml.composenav.swipetodismiss.ui.theme.SwipetodismissCompoeNavigationTheme

// ── Navigation Keys ─────────────────────────────────────────────────────────

data object HomeKey : NavKey

data class DetailKey(
    val id: Int,
    val title: String,
) : NavKey

// ── Palette ─────────────────────────────────────────────────────────────────

private val screenColors = listOf(
    Color(0xFF6200EA),
    Color(0xFF03DAC5),
    Color(0xFFBB86FC),
    Color(0xFF018786),
    Color(0xFFCF6679),
    Color(0xFF3700B3),
    Color(0xFF03A9F4),
    Color(0xFFFF5722),
)

// ── Activity ────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwipetodismissCompoeNavigationTheme {
                SwipeDismissDemo()
            }
        }
    }
}

// ── Root ─────────────────────────────────────────────────────────────────────

@Composable
private fun SwipeDismissDemo() {
    val backStack = remember { mutableStateListOf<NavKey>(HomeKey) }

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLast() },
        sceneStrategy = SwipeToDismissSceneStrategy() then SinglePaneSceneStrategy(),
        entryProvider = entryProvider {
            entry<HomeKey> { _ ->
                HomeScreen(
                    onItemClick = { key -> backStack.add(key) },
                )
            }

            swipeToDismissHorizontalEntry<DetailKey> { key ->
                DetailScreen(
                    key = key,
                    onBack = { if (backStack.size > 1) backStack.removeLast() },
                )
            }
        },
    )
}

// ── Home Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(onItemClick: (DetailKey) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Swipe to Dismiss Demo") })
        },
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count = 20) { index ->
                val color = screenColors[index % screenColors.size]
                ListItem(
                    headlineContent = { Text("Item #${index + 1}") },
                    supportingContent = { Text("Tap to open, then swipe right to dismiss") },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = color,
                            )
                        }
                    },
                    modifier = Modifier.clickable {
                        onItemClick(DetailKey(id = index, title = "Item #${index + 1}"))
                    },
                )
            }
        }
    }
}

// ── Detail Screen ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(key: DetailKey, onBack: () -> Unit) {
    val color = screenColors[key.id % screenColors.size]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(key.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = color.copy(alpha = 0.12f),
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Hero banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "#${key.id + 1}",
                        style = MaterialTheme.typography.displayLarge,
                        color = color,
                    )
                }
            }

            // Instruction
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "\u2190 Swipe from the left edge to go back",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Scroll the list to the top and keep pulling right — " +
                            "nested scroll will hand off to the dismiss gesture.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Scrollable content to demonstrate nested scroll hand-off
            items(count = 30) { index ->
                ListItem(
                    headlineContent = { Text("Content row ${index + 1}") },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(color.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                            )
                        }
                    },
                )
            }
        }
    }
}

