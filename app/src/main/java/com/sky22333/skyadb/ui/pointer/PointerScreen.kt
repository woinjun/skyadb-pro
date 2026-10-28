package com.sky22333.skyadb.ui.pointer

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sky22333.skyadb.R
import com.sky22333.skyadb.ui.components.AppTopBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PointerScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: PointerController = viewModel<PointerViewModel>(),
    bluetooth: Boolean = false,
    onConnectDevice: (() -> Unit)? = null,
    connectionContent: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sensors = remember(context) { context.getSystemService(SensorManager::class.java) }
    val gyroscope = remember(sensors) { sensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    val touchLabel = stringResource(R.string.pointer_touch_hint)

    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) viewModel.connect()
            if (event == Lifecycle.Event.ON_STOP) viewModel.disconnect()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.connect()
        onDispose { lifecycle.removeObserver(observer); viewModel.disconnect() }
    }
    DisposableEffect(view, state.ready) {
        val previous = view.keepScreenOn
        if (state.ready) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    DisposableEffect(sensors, gyroscope, state.flying, state.ready) {
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                viewModel.sensor(event.timestamp, event.values, view.display?.rotation ?: 0)
            }
        }
        if (state.flying && state.ready && gyroscope != null) {
            if (sensors?.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_GAME) != true) {
                viewModel.flying(false)
            }
        }
        onDispose { sensors?.unregisterListener(listener) }
    }

    Column(Modifier.fillMaxSize().padding(bottom = bottomPadding)) {
        AppTopBar(
            title = { Text(stringResource(if (bluetooth) R.string.bluetooth_pointer_title else R.string.pointer_title)) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back))
                }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(if (bluetooth) R.string.bluetooth_intro else R.string.pointer_intro), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(state.status), color = if (state.detail.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            if (state.detail.isNotEmpty()) Text(state.detail, style = MaterialTheme.typography.bodySmall)
            connectionContent()
            if (!bluetooth && !state.ready && !state.connecting) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::connect) { Text(stringResource(R.string.pointer_retry)) }
                    if (onConnectDevice != null) OutlinedButton(onClick = onConnectDevice) {
                        Text(stringResource(R.string.action_go_connect))
                    }
                }
            }
            if (state.connecting) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(
                Modifier.fillMaxWidth().height(250.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
                    .semantics {
                        contentDescription = touchLabel
                        if (state.ready) onClick { viewModel.click(); true }
                    }
                    .pointerInput(state.ready) {
                        if (!state.ready) return@pointerInput
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var distance = 0f
                            var fingers = 1
                            do {
                                val event = awaitPointerEvent()
                                val moving = event.changes.filter { it.pressed && it.previousPressed }
                                fingers = maxOf(fingers, event.changes.count { it.pressed })
                                if (moving.isNotEmpty()) {
                                    val dx = moving.sumOf { it.positionChange().x.toDouble() }.toFloat() / moving.size
                                    val dy = moving.sumOf { it.positionChange().y.toDouble() }.toFloat() / moving.size
                                    distance += kotlin.math.sqrt(dx * dx + dy * dy)
                                    if (distance > viewConfiguration.touchSlop) {
                                        if (moving.size >= 2) viewModel.scroll(-dy / (32.dp.toPx()))
                                        else if (fingers == 1) viewModel.move(dx, dy)
                                    }
                                }
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            if (distance <= viewConfiguration.touchSlop && fingers == 1) viewModel.click()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text(touchLabel, modifier = Modifier.padding(24.dp)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.click() }, enabled = state.ready) { Text(stringResource(R.string.pointer_left)) }
                OutlinedButton(onClick = { viewModel.click(true) }, enabled = state.ready) { Text(stringResource(R.string.pointer_right)) }
                FilterChip(
                    selected = state.dragging, onClick = viewModel::toggleDrag, enabled = state.ready,
                    label = { Text(stringResource(R.string.pointer_drag)) },
                )
            }
            Text(stringResource(R.string.pointer_sensitivity, state.sensitivity), style = MaterialTheme.typography.labelLarge)
            Slider(value = state.sensitivity, onValueChange = viewModel::sensitivity, valueRange = 0.3f..3f)
            HorizontalDivider()
            Text(stringResource(if (bluetooth) R.string.bluetooth_air_title else R.string.pointer_air_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pointer_air_hint), style = MaterialTheme.typography.bodySmall)
            if (gyroscope == null) Text(stringResource(R.string.pointer_no_gyro), color = MaterialTheme.colorScheme.error)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(
                    checked = state.flying, onCheckedChange = viewModel::flying,
                    enabled = state.ready && gyroscope != null,
                )
                Text(stringResource(if (state.flying) R.string.pointer_air_on else R.string.pointer_air_off))
            }
            if (state.flying) {
                Text(stringResource(if (state.calibrated) R.string.pointer_calibrated else R.string.pointer_calibrating))
                OutlinedButton(onClick = viewModel::recalibrate) { Text(stringResource(R.string.pointer_calibrate)) }
            }
        }
    }
}
