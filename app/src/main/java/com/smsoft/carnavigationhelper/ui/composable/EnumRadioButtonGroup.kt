package com.smsoft.carnavigationhelper.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smsoft.carnavigationhelper.data.NavType

@Composable
fun EnumRadioButtonGroup(
    value: NavType,
    onOptionSelected: (NavType) -> Unit
) {
    Column {
        NavType.entries.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (item == value),
                        onClick = { onOptionSelected(item) }
                    )
                    .padding(8.dp)
            ) {
                RadioButton(
                    selected = (item == value),
                    onClick = null
                )
                Text(
                    text = stringResource(item.resId),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}