package com.healthconnect.export.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.healthconnect.export.R
import com.healthconnect.export.data.HealthDataType

private enum class DataTypeCategory(val icon: ImageVector) {
    ACTIVITY(Icons.Default.Bolt),
    VITALS(Icons.Default.Favorite),
    BODY(Icons.Default.Person)
}

@Composable
private fun dataTypeCategoryDisplayName(category: DataTypeCategory): String = when (category) {
    DataTypeCategory.ACTIVITY -> stringResource(R.string.category_activity)
    DataTypeCategory.VITALS -> stringResource(R.string.category_vitals)
    DataTypeCategory.BODY -> stringResource(R.string.category_body)
}

private fun categoryForType(type: HealthDataType): DataTypeCategory = when (type) {
    HealthDataType.STEPS, HealthDataType.DISTANCE, HealthDataType.FLOORS_CLIMBED,
    HealthDataType.CALORIES, HealthDataType.ACTIVE_CALORIES,
    HealthDataType.EXERCISE, HealthDataType.SPEED -> DataTypeCategory.ACTIVITY

    HealthDataType.HEART_RATE, HealthDataType.RESTING_HEART_RATE,
    HealthDataType.BLOOD_PRESSURE, HealthDataType.BLOOD_GLUCOSE,
    HealthDataType.OXYGEN_SATURATION, HealthDataType.BODY_TEMPERATURE,
    HealthDataType.RESPIRATORY_RATE -> DataTypeCategory.VITALS

    HealthDataType.WEIGHT, HealthDataType.BODY_FAT, HealthDataType.HYDRATION,
    HealthDataType.SLEEP, HealthDataType.NUTRITION, HealthDataType.MENSTRUATION -> DataTypeCategory.BODY
}

private val dataTypesByCategory: Map<DataTypeCategory, List<HealthDataType>> by lazy {
    HealthDataType.entries.groupBy { categoryForType(it) }
}

@Composable
fun DataTypeCard(
    selectedTypes: Set<HealthDataType>,
    onTypeToggle: (HealthDataType) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    val allSelected = selectedTypes.size == HealthDataType.entries.size
    val noneSelected = selectedTypes.isEmpty()

    val expandedStates = remember {
        mutableStateMapOf<DataTypeCategory, Boolean>().apply {
            DataTypeCategory.entries.forEach { put(it, true) }
        }
    }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.data_types),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (noneSelected) MaterialTheme.colorScheme.surfaceVariant
                             else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "${selectedTypes.size} / ${HealthDataType.entries.size}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (noneSelected) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Select / Deselect All
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSelectAll,
                    enabled = !allSelected,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(stringResource(R.string.select_all), style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onDeselectAll,
                    enabled = !noneSelected,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(stringResource(R.string.deselect_all), style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expand / Collapse All buttons
            val allExpanded = DataTypeCategory.entries.all { expandedStates[it] == true }
            val allCollapsed = DataTypeCategory.entries.all { expandedStates[it] == false }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        DataTypeCategory.entries.forEach { expandedStates[it] = true }
                    },
                    enabled = !allExpanded,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(stringResource(R.string.expand_all), style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = {
                        DataTypeCategory.entries.forEach { expandedStates[it] = false }
                    },
                    enabled = !allCollapsed,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(stringResource(R.string.collapse_all), style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Collapsible categories
            DataTypeCategory.entries.forEachIndexed { index, category ->
                val types = dataTypesByCategory[category] ?: return@forEachIndexed
                val selectedInCategory = types.count { selectedTypes.contains(it) }
                val expanded = expandedStates[category] ?: true

                // Clickable category header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedStates[category] = !expanded
                        }
                        .padding(vertical = 4.dp)
                ) {
                    // Icon badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = dataTypeCategoryDisplayName(category),
                            modifier = Modifier
                                .padding(6.dp)
                                .size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    // Title
                    Text(
                        dataTypeCategoryDisplayName(category),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )

                    // Selected counter
                    Text(
                        "$selectedInCategory / ${types.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    // Animated chevron
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        animationSpec = tween(durationMillis = 200),
                        label = "chevronRotation"
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (expanded) stringResource(R.string.collapse_section)
                                             else stringResource(R.string.expand_section),
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(chevronRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Animated content
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(
                        animationSpec = tween(durationMillis = 250)
                    ),
                    exit = shrinkVertically(
                        animationSpec = tween(durationMillis = 200)
                    )
                ) {
                    val mid = (types.size + 1) / 2
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            types.take(mid).forEach { type ->
                                DataTypeRow(type, selectedTypes, onTypeToggle)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            types.drop(mid).forEach { type ->
                                DataTypeRow(type, selectedTypes, onTypeToggle)
                            }
                        }
                    }
                }

                // Spacer between categories
                if (index < DataTypeCategory.entries.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun DataTypeRow(
    type: HealthDataType,
    selectedTypes: Set<HealthDataType>,
    onTypeToggle: (HealthDataType) -> Unit
) {
    val displayName = healthDataTypeDisplayName(type)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        Checkbox(
            checked = selectedTypes.contains(type),
            onCheckedChange = { onTypeToggle(type) },
            modifier = Modifier.size(20.dp)
        )
        Icon(
            imageVector = iconForType(type),
            contentDescription = displayName,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            displayName,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun healthDataTypeDisplayName(type: HealthDataType): String = when (type) {
    HealthDataType.STEPS -> stringResource(R.string.data_type_STEPS)
    HealthDataType.HEART_RATE -> stringResource(R.string.data_type_HEART_RATE)
    HealthDataType.SLEEP -> stringResource(R.string.data_type_SLEEP)
    HealthDataType.CALORIES -> stringResource(R.string.data_type_CALORIES)
    HealthDataType.DISTANCE -> stringResource(R.string.data_type_DISTANCE)
    HealthDataType.FLOORS_CLIMBED -> stringResource(R.string.data_type_FLOORS_CLIMBED)
    HealthDataType.ACTIVE_CALORIES -> stringResource(R.string.data_type_ACTIVE_CALORIES)
    HealthDataType.WEIGHT -> stringResource(R.string.data_type_WEIGHT)
    HealthDataType.BODY_FAT -> stringResource(R.string.data_type_BODY_FAT)
    HealthDataType.BLOOD_PRESSURE -> stringResource(R.string.data_type_BLOOD_PRESSURE)
    HealthDataType.BLOOD_GLUCOSE -> stringResource(R.string.data_type_BLOOD_GLUCOSE)
    HealthDataType.OXYGEN_SATURATION -> stringResource(R.string.data_type_OXYGEN_SATURATION)
    HealthDataType.BODY_TEMPERATURE -> stringResource(R.string.data_type_BODY_TEMPERATURE)
    HealthDataType.RESPIRATORY_RATE -> stringResource(R.string.data_type_RESPIRATORY_RATE)
    HealthDataType.HYDRATION -> stringResource(R.string.data_type_HYDRATION)
    HealthDataType.RESTING_HEART_RATE -> stringResource(R.string.data_type_RESTING_HEART_RATE)
    HealthDataType.EXERCISE -> stringResource(R.string.data_type_EXERCISE)
    HealthDataType.NUTRITION -> stringResource(R.string.data_type_NUTRITION)
    HealthDataType.SPEED -> stringResource(R.string.data_type_SPEED)
    HealthDataType.MENSTRUATION -> stringResource(R.string.data_type_MENSTRUATION)
}

private fun iconForType(type: HealthDataType): ImageVector = when (type) {
    HealthDataType.STEPS -> Icons.Default.DirectionsWalk
    HealthDataType.HEART_RATE -> Icons.Default.Favorite
    HealthDataType.SLEEP -> Icons.Default.Nightlight
    HealthDataType.CALORIES -> Icons.Default.LocalFireDepartment
    HealthDataType.DISTANCE -> Icons.Default.Straighten
    HealthDataType.FLOORS_CLIMBED -> Icons.Default.Stairs
    HealthDataType.ACTIVE_CALORIES -> Icons.Default.Bolt
    HealthDataType.WEIGHT -> Icons.Default.MonitorWeight
    HealthDataType.BODY_FAT -> Icons.Default.Scale
    HealthDataType.BLOOD_PRESSURE -> Icons.Default.FavoriteBorder
    HealthDataType.BLOOD_GLUCOSE -> Icons.Default.Bloodtype
    HealthDataType.OXYGEN_SATURATION -> Icons.Default.Air
    HealthDataType.BODY_TEMPERATURE -> Icons.Default.DeviceThermostat
    HealthDataType.RESPIRATORY_RATE -> Icons.Default.Air
    HealthDataType.HYDRATION -> Icons.Default.WaterDrop
    HealthDataType.RESTING_HEART_RATE -> Icons.Default.FavoriteBorder
    HealthDataType.EXERCISE -> Icons.Default.FitnessCenter
    HealthDataType.NUTRITION -> Icons.Default.Restaurant
    HealthDataType.SPEED -> Icons.Default.Speed
    HealthDataType.MENSTRUATION -> Icons.Default.CalendarMonth
}
