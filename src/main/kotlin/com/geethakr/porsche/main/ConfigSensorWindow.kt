package com.geethakr.porsche.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import com.geethakr.porsche.protobuf.SensorDataProto
import com.geethakr.porsche.protobuf.SensorDataProto.SensorData
import kotlinx.coroutines.*
import java.io.PrintWriter
import java.net.Socket

@Composable
fun configureSensorsWindow(sensorData: MutableState<SensorData?>, coroutineScope: CoroutineScope, serverJob: MutableState<Job?>,
                           clientSocket: MutableState<Socket?>, onSubmit: () -> Unit) {
    val pressure = remember { mutableStateOf(sensorData.value?.pressure?.toString() ?: "") }
    val tpms = remember { mutableStateOf(sensorData.value?.tpms?.toString() ?: "") }
    val temperature = remember { mutableStateOf(sensorData.value?.temperature?.toString() ?: "") }
    val lightsOn = remember { mutableStateOf(sensorData.value?.lightsOn ?: false) }
    val fuelLevel = remember { mutableStateOf(sensorData.value?.fuelLevel ?: 50) }


    val realTimeTemperature = Weather


    Column(
        modifier = Modifier.padding(24.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        //Heading
        Text("Configure Sensor Data", style = MaterialTheme.typography.h5)

        //Pressure field
        OutlinedTextField(
            value = pressure.value,
            onValueChange = { pressure.value = it },
            label = { Text("Pressure Sensor (psi)") },
            modifier = Modifier.fillMaxWidth()
        )

        //Lights Toggle field
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Lights")
            Switch(
                checked = lightsOn.value,
                onCheckedChange = { lightsOn.value = it }
            )
        }

        //TPMS field
        OutlinedTextField(
            value = tpms.value,
            onValueChange = { tpms.value = it },
            label = { Text("TPMS Value") },
            modifier = Modifier.fillMaxWidth()
        )

        //Temperature field
        OutlinedTextField(
            value = temperature.value,
            onValueChange = { temperature.value = it },
            label = { Text("Temperature Sensor (°C)") },
            modifier = Modifier.fillMaxWidth()
        )

        //Fuel slider field
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Fuel Sensor Level: ${fuelLevel.value}%")
            Slider(
                value = fuelLevel.value.toFloat(),
                onValueChange = { fuelLevel.value = it.toInt() },
                valueRange = 0f..100f
            )
        }

        //Submit
        Button(
            onClick = {
                println("Collected Data:")
                println("Pressure: ${pressure.value}")
                println("Lights: ${if (lightsOn.value) "On" else "Off"}")
                println("TPMS: ${tpms.value}")
                println("Temperature: ${temperature.value}")
                println("Fuel Level: ${fuelLevel.value}%")
                val sensorData = SensorData.newBuilder()
                    .setPressure(pressure.value.toFloat())
                    .setLightsOn(lightsOn.value)
                    .setTpms(tpms.value.toFloat())
                    .setTemperature(temperature.value.toFloat())
                    .setFuelLevel(fuelLevel.value)
                    .build()


                coroutineScope.launch(Dispatchers.IO) {
                    clientSocket.value.let { socket ->
                        clientSocket.value?.let {
                            sendMessage(
                                socket = it,sensorData
                            )
                        }
                    }
                }
                onSubmit
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Submit")
        }
    }
}

/**
 * Function to send the message from server to client
 */
suspend fun sendMessage(socket: Socket, sensorData:SensorData) {
    withContext(Dispatchers.IO) {
        try {
            sensorData.writeDelimitedTo(socket.getOutputStream())
            socket
                .getOutputStream().flush()
//            val writer = PrintWriter(socket.getOutputStream(), true)
//            writer.println(sensorData.writeDelimitedTo(socket.getOutputStream()))
        } catch (e: Exception) {
            println("Error sending message: ${e.message}")
            e.printStackTrace()
        }
    }
}