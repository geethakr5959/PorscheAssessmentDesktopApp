// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.geethakr.porsche.main.configureSensorsWindow
import com.geethakr.porsche.main.sendMessage
import com.geethakr.porsche.protobuf.SensorDataProto.SensorData
import com.geethakr.porsche.uiComponents.simpleButton
import kotlinx.coroutines.*
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

lateinit var msgLogList: MutableList<String>

/**
 * Main function of the Emulator App
 */
fun main() = application {
    var isWindowOpen by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val serverJob = remember { mutableStateOf<Job?>(null) }
    val clientSocket = remember { mutableStateOf<Socket?>(null) }
    val data = remember { mutableStateOf<SensorData?>(null) }

    //MAIN WINDOW
    Window(onCloseRequest = ::exitApplication, title = "SERVER EMULATOR") {
        mainWindow(data, coroutineScope, serverJob, clientSocket, onOpenConfigSensorWindow = { isWindowOpen = it })
    }

    //CONFIGURE SENSOR WINDOW
    if (isWindowOpen) {
        Window(onCloseRequest = {isWindowOpen=false}, title = "Configure Sensor Data") {
            configureSensorsWindow(data, coroutineScope, serverJob, clientSocket) {
                isWindowOpen=false
            }
        }
    }
}

/**
 * Composable Function to show the mainwindow of the emulator app
 */
@Composable
@Preview
fun mainWindow(data: MutableState<SensorData?>,
                   coroutineScope: CoroutineScope, serverJob: MutableState<Job?>,
               clientSocket: MutableState<Socket?>, onOpenConfigSensorWindow: (Boolean) -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row {

            //Leftside Pane UI
            Surface(modifier = Modifier.width(300.dp), color = Color.Gray) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    showStartButton(data, coroutineScope, serverJob, clientSocket)
                    showConfigSensorButton(onOpenConfigSensorWindow)
                    showDisconnectButton()
                    showReceiveCANDataButton(coroutineScope, clientSocket)
                }
            }

            //Rightside Pane UI
            Surface(
                modifier = Modifier.background(Color.Black).fillMaxWidth().fillMaxHeight().padding(10.dp),
                color = Color.Black
            ) {
                msgLogList = remember { mutableStateListOf("Server not started") }
                showExchangingMessageList(msgLogList.toList())
            }
        }
    }
}

/**
 * Composable function to handle the start button ui and behavior
 */
@Composable
private fun showStartButton(data: MutableState<SensorData?>,coroutineScope: CoroutineScope, serverJob: MutableState<Job?>,
                            clientSocket: MutableState<Socket?>) {
    simpleButton("START", width = 300.dp, onClickAction = {
        establishConnection(data, coroutineScope, serverJob, clientSocket)
    })
}

/**
 * Composable function to handle the Configure Sensor button ui and behavior
 */
@Composable
private fun showConfigSensorButton(onOpenConfigSensorWindow: (Boolean) -> Unit) {
    simpleButton("CONFIGURE SENSORS", width = 300.dp, onClickAction = { onOpenConfigSensorWindow(true) })
}

/**
 * Composable function to handle the disconnect button ui and behavior
 */
@Composable
private fun showDisconnectButton() {
    simpleButton("DISCONNECT", width = 300.dp, onClickAction = {
        disconnect()
    })
}

/**
 * Composable function to handle the receive CAN data button ui and behavior
 */
@Composable
private fun showReceiveCANDataButton(coroutineScope: CoroutineScope, clientSocket: MutableState<Socket?>) {
    simpleButton("SIMULATE CAN Data", width = 300.dp, onClickAction = {
        thread {
            //For every 1 sec CAN msg sent to UI
            while (true) {
                val canMsg = simulateCANMessage()
                val sensorData = parseCANToSensorData(canMsg)
                coroutineScope.launch(Dispatchers.IO) {
                    sendMessage(clientSocket.value!!, sensorData)
                }
                msgLogList.add("CAN sensorData tpms---> ${sensorData.tpms}," +
                        "pressure---> ${sensorData.pressure}," +
                        "temperature--> ${sensorData.temperature}")
                Thread.sleep(1000)
            }
        }
    })
}

/**
 * Composable function to show the right side pane UI that displays the list of messages exchanged between
 * the emulator and UI
 */
@Composable
fun showExchangingMessageList(list: List<String>) {
    LazyColumn(state = LazyListState(list.size)) {
        var color: Color
        items(items = list) {
            if (it.isNotEmpty()) {
                color = Color.White
                Text(text = it, color = color, fontSize = 16.sp)
            }
        }
    }
}


/**
 * Function to establish the TCP/IP connection between desktop app server and android UI client
 */
fun establishConnection(data: MutableState<SensorData?>,
    coroutineScope: CoroutineScope, serverJob: MutableState<Job?>,
    clientSocket: MutableState<Socket?>
) {
    //TCP Server will be started here and waiting for the client to get connected.
    msgLogList.add("Starting server...")

    if (serverJob.value == null) {

        serverJob.value = coroutineScope.launch(Dispatchers.IO) {
            var serverSocket: ServerSocket? = null
            try {

                serverSocket = ServerSocket(6666)
                msgLogList.add("Server started on port ${serverSocket.localPort}. Waiting for a client...")

                while (isActive) {
                    val newClientSocket: Socket = serverSocket.accept()
                    //connected client's socket is stored
                    clientSocket.value = newClientSocket
                    msgLogList.add("Client is connected: ${newClientSocket.inetAddress.hostAddress}. Listening for messages...")

                    launch(Dispatchers.IO) {
                        handleClientConnection(newClientSocket) { message ->
                            data.value = message
                            msgLogList.add("Received msg from Client: Pressure --->${message.pressure}, TPMS ----> ${message.tpms}, " +
                                    "Lights--->${message.lightsOn}, Fuel ---> ${message.fuelLevel}")

                        }
                    }
                }
            } catch (e: Exception) {
                msgLogList.add("Error: ${e.message}")
                e.printStackTrace()
            } finally {
                serverSocket?.close()
                serverJob.value = null
                clientSocket.value = null
            }
        }
    } else {
        serverJob.value!!.cancel()
        serverJob.value = null
        msgLogList.clear()
        msgLogList.add("Server stopped")
        clientSocket.value = null
    }
}

/**
 * Function to handle the client socket connection and manage the message received
 */
suspend fun handleClientConnection(socket: Socket, onMessageReceived: (SensorData) -> Unit) {
    withContext(Dispatchers.IO) {
        try {

            val input: InputStream = socket.getInputStream()

            while (isActive) {
                val sensorData = SensorData.parseDelimitedFrom(input)
                if (sensorData != null) {
                    onMessageReceived(sensorData)
                } else {
                    break
                }
            }
        }catch (e: Exception) {
            msgLogList.add("Error during client communication: ${e.message}")
            e.printStackTrace()
        } finally {
            socket.close()
        }
    }
}

/**
 * Function to disconnect the server and the sockets created
 */
fun disconnect() {
    msgLogList.clear()
    msgLogList.add("Server stopped")
}

//Sample data class to simulate the CAN msg
data class CANMessage(val id: Int, val data: ByteArray)

// Simulate a CAN message
fun simulateCANMessage(): CANMessage {
    return CANMessage(
        id = 0x100,
        data = byteArrayOf(0x42, 0x48, 0x00, 0x00,  //pressure (50.0f)
            0x42, 0x60, 0x00, 0x00)  //tpms (56.0f)
    )
}

/**
 * Function to parse the CAN msg into SensorData protobuf format
 */
fun parseCANToSensorData(msg: CANMessage): SensorData {
    val buffer = ByteBuffer.wrap(msg.data).order(ByteOrder.BIG_ENDIAN)
    val pressure = buffer.float
    val tpms = buffer.float

    return SensorData.newBuilder()
        .setPressure(pressure)
        .setTpms(tpms)
        .build()
}