package com.healthconnect.export.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.healthconnect.export.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

@Composable
fun DateRangeCard(
    startDate: LocalDate,
    endDate: LocalDate,
    onDateRangeChange: (LocalDate, LocalDate) -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate) -> Unit
) {
    val context = LocalContext.current

    val preset7dStart = LocalDate.now().minusDays(6)
    val preset7dEnd = LocalDate.now()
    val preset30dStart = LocalDate.now().minusDays(29)
    val preset30dEnd = LocalDate.now()

    val is7dPreset = startDate == preset7dStart && endDate == preset7dEnd
    val is30dPreset = startDate == preset30dStart && endDate == preset30dEnd

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.export_period), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 7 days preset
                if (is7dPreset) {
                    Button(
                        onClick = { onDateRangeChange(preset7dStart, preset7dEnd) }
                    ) {
                        Text(stringResource(R.string.days_7))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onDateRangeChange(preset7dStart, preset7dEnd) }
                    ) {
                        Text(stringResource(R.string.days_7))
                    }
                }
                // 30 days preset
                if (is30dPreset) {
                    Button(
                        onClick = { onDateRangeChange(preset30dStart, preset30dEnd) }
                    ) {
                        Text(stringResource(R.string.days_30))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onDateRangeChange(preset30dStart, preset30dEnd) }
                    ) {
                        Text(stringResource(R.string.days_30))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DatePickerButton(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.from_label),
                    date = startDate,
                    onDateSelected = onStartDateChange
                )
                DatePickerButton(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.to_label),
                    date = endDate,
                    onDateSelected = onEndDateChange
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.health_connect_access_limit),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun DatePickerButton(
    modifier: Modifier = Modifier,
    label: String,
    date: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val context = LocalContext.current

    // Check if date is at a preset value (defaults)
    val isPreset = date == LocalDate.now().minusDays(6) ||
        date == LocalDate.now() ||
        date == LocalDate.now().minusDays(29)

    val containerColor = if (isPreset) MaterialTheme.colorScheme.surfaceVariant
                         else MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (isPreset) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onPrimaryContainer
    val labelColor = if (isPreset) MaterialTheme.colorScheme.onSurfaceVariant
                     else MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier
            .height(56.dp)
            .clickable {
                val calendar = Calendar.getInstance()
                calendar.set(date.year, date.monthValue - 1, date.dayOfMonth)
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        onDateSelected(LocalDate.of(year, month + 1, day))
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor
                )
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
            }
        }
    }
}
