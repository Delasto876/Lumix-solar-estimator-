package com.lumix.estimator.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumix.estimator.data.PriceRepository
import com.lumix.estimator.domain.PriceFields
import com.lumix.estimator.domain.PriceList
import com.lumix.estimator.ui.components.NumberField
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceSettingsScreen(priceRepository: PriceRepository, onBack: () -> Unit) {
    var editingDiscount by remember { mutableStateOf(false) }
    val regular by priceRepository.regularPrices.collectAsState(initial = PriceList.DEFAULT)
    val discount by priceRepository.discountPrices.collectAsState(initial = PriceList.DEFAULT)
    val scope = rememberCoroutineScope()
    val current = if (editingDiscount) discount else regular

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Price List Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("×", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                listOf(false, true).forEachIndexed { index, isDiscount ->
                    SegmentedButton(
                        selected = editingDiscount == isDiscount,
                        onClick = { editingDiscount = isDiscount },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)
                    ) {
                        Text(if (isDiscount) "Discount price list" else "Regular price list")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PriceFields.groups.forEach { group ->
                    item(key = "header_$group") {
                        Text(
                            group,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(PriceFields.all.filter { it.group == group }, key = { "${group}_${it.key}" }) { field ->
                        NumberField(
                            label = field.label,
                            value = field.get(current),
                            onValueChange = { v ->
                                val updated = field.set(current, v)
                                scope.launch {
                                    if (editingDiscount) priceRepository.updateDiscount(updated) else priceRepository.updateRegular(updated)
                                }
                            },
                            suffix = "J$"
                        )
                    }
                }
                item(key = "reset_button") {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                if (editingDiscount) priceRepository.resetDiscountToDefault() else priceRepository.resetRegularToDefault()
                            }
                        }) {
                            Text("Reset ${if (editingDiscount) "discount" else "regular"} prices to default")
                        }
                    }
                }
            }
        }
    }
}
