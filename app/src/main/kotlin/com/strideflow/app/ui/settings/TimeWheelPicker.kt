package com.strideflow.app.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strideflow.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelColumn(
    items: List<Int>,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit,
    format: (Int) -> String = { "%02d".format(it) },
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val itemHeight = 42.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    val initialIndex = remember(selectedValue) { items.indexOf(selectedValue).coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Snapping update when user finishes scrolling
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centerIndex = listState.firstVisibleItemIndex +
                if (listState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
            val clampedIndex = centerIndex.coerceIn(0, items.lastIndex)
            val selected = items[clampedIndex]
            if (selected != selectedValue) {
                onValueSelected(selected)
            }
        }
    }

    // Scroll to value if changed from outside
    LaunchedEffect(selectedValue) {
        val targetIndex = items.indexOf(selectedValue).coerceAtLeast(0)
        if (!listState.isScrollInProgress && listState.firstVisibleItemIndex != targetIndex) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Step Up Button
        IconButton(
            onClick = {
                val currentIndex = items.indexOf(selectedValue)
                if (currentIndex > 0) {
                    val prev = items[currentIndex - 1]
                    coroutineScope.launch {
                        listState.animateScrollToItem(currentIndex - 1)
                        onValueSelected(prev)
                    }
                }
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Increase",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        Box(
            modifier = Modifier
                .width(76.dp)
                .height(itemHeight * 3),
            contentAlignment = Alignment.Center
        ) {
            // High-contrast neon center selection frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.5.dp, NeonGreen, RoundedCornerShape(10.dp))
            )

            LazyColumn(
                state = listState,
                flingBehavior = snapFlingBehavior,
                contentPadding = PaddingValues(vertical = itemHeight),
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(items.size) { index ->
                    val item = items[index]
                    val isSelected = item == selectedValue
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .clickable {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index)
                                    onValueSelected(item)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = format(item),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                            fontSize = if (isSelected) 22.sp else 16.sp,
                            color = if (isSelected) NeonGreen else TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Step Down Button
        IconButton(
            onClick = {
                val currentIndex = items.indexOf(selectedValue)
                if (currentIndex < items.lastIndex) {
                    val next = items[currentIndex + 1]
                    coroutineScope.launch {
                        listState.animateScrollToItem(currentIndex + 1)
                        onValueSelected(next)
                    }
                }
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Decrease",
                tint = TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun TimeWheelPicker(
    hour: Int,
    minute: Int,
    onTimeChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hoursList = remember { (0..23).toList() }
    val minutesList = remember { (0..59).toList() }

    val periodStr = if (hour < 12) "AM" else "PM"
    val display12Hour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    val formattedSummary = "%02d:%02d (%d:%02d %s)".format(hour, minute, display12Hour, minute, periodStr)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceElevated)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TARGET WORKOUT TIME",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Scroll to set your preferred daily training time",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            Text(
                text = formattedSummary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = BrightCyan
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "HOURS (00–23)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                WheelColumn(
                    items = hoursList,
                    selectedValue = hour,
                    onValueSelected = { newHour -> onTimeChanged(newHour, minute) }
                )
            }

            Spacer(modifier = Modifier.width(18.dp))

            Text(
                text = ":",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = NeonGreen,
                modifier = Modifier.padding(top = 10.dp)
            )

            Spacer(modifier = Modifier.width(18.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "MINS (00–59)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                WheelColumn(
                    items = minutesList,
                    selectedValue = minute,
                    onValueSelected = { newMinute -> onTimeChanged(hour, newMinute) }
                )
            }
        }
    }
}
