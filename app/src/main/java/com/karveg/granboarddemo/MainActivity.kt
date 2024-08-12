package com.karveg.granboarddemo

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.karveg.granboarddemo.models.DartData
import com.karveg.granboarddemo.models.LedData
import com.karveg.granboarddemo.models.RGB
import com.karveg.granboarddemo.store.DataStore
import com.lb.vector_child_finder_library.VectorChildFinder
import java.util.UUID
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var scanning: Boolean = false
    private lateinit var scanResults: MutableList<BluetoothDevice>
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    private val deviceList: MutableList<String> = mutableListOf()
    private var deviceConnected: BluetoothDevice? = null
    private var gattDeviceDiana: BluetoothGatt? = null
    private var connected: Boolean = false

    private lateinit var buttonScan: Button
    private lateinit var buttonConnect: Button
    private lateinit var buttonSend: Button
    private lateinit var listView: ListView
    private lateinit var editText: EditText
    private lateinit var shotView: TextView
    private lateinit var imageView: ImageView
    private lateinit var vectorDrawable: Drawable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the ActivityResultLauncher for requesting permissions
        requestPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions: Map<String, Boolean> ->
            handlePermissionsResult(permissions)
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        // Get BluetoothAdapter from BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth no está soportado", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Verificar si BLE está soportado
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE no está soportado", Toast.LENGTH_SHORT).show()
            finish()
        }

        bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner
        scanResults = mutableListOf()

        eventsUI()
    }

    override fun onResume() {
        super.onResume()
        // Verificar y solicitar permisos en onResume
        if (!hasPermissions()) {
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun eventsUI() {

        buttonScan = findViewById(R.id.scan_button)
        buttonConnect = findViewById(R.id.stateConnection)
        buttonSend = findViewById(R.id.sendShot)
        listView = findViewById(R.id.devices_list)
        editText = findViewById(R.id.text)
        shotView = findViewById(R.id.shotView)
        imageView = findViewById<ImageView>(R.id.imageView)
        vectorDrawable = ContextCompat.getDrawable(this, R.drawable.dartboard)!!
        editText.setText("D20")

        buttonScan.setOnClickListener {
            if (!scanning) startScan() else stopScan()
        }

        // Configurar ListView
        deviceListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        listView.adapter = deviceListAdapter

        // Configurar el listener para clic largo
        listView.setOnItemClickListener { _, _, position, _ ->
            // Obtén el elemento pulsado
            val item = deviceList[position].split("·")[1].trim()
            Log.e("GattCallback", item)

            deviceConnected = scanResults.find { it.address.lowercase() == item.lowercase() }
            if (deviceConnected != null)
                gattDeviceDiana = deviceConnected!!.connectGatt(this, false, gattCallback)

        }

        buttonConnect.text = "Disconnected"
        buttonConnect.setOnClickListener {
            if (connected) {
                gattDeviceDiana?.close()
                runOnUiThread {
                    listView.visibility = View.VISIBLE
                    buttonScan.visibility = View.VISIBLE
                    buttonConnect.visibility = View.GONE
                }
                connected = false;
            }
        }

        buttonSend.setOnClickListener {
            if (editText.text != null) {
                val dataDart: DartData? =
                    DataStore.dartDataList.find { it.pathBoard == editText.text.toString().uppercase() }
                if (dataDart != null) {
                    showShot(dataDart.dataBoard)
                    editText.setText("")
                }
            }
        }
    }

    //region Permisos
    private fun hasPermissions(): Boolean {
        return (checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    private fun handlePermissionsResult(permissions: Map<String, Boolean>) {
        val allPermissionsGranted = permissions.values.all { it }
        if (allPermissionsGranted) {
            // All permissions granted, proceed with desired functionality
            Toast.makeText(this, "Todos los permisos concedidos.", Toast.LENGTH_SHORT).show()
        } else {
            // Some permissions were denied, handle accordingly
            Toast.makeText(
                this,
                "Todos los permisos son necesarios para la funcionalidad completa.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    //endregion

    //region Scan
    @SuppressLint("MissingPermission")
    private fun startScan() {
        val scanPerior: Long = 10000 // Escanea por 10 segundos
        // Detener el escaneo después de un período definido
        Handler(Looper.getMainLooper()).postDelayed({ stopScan() }, scanPerior)
        deviceList.clear()
        scanning = true
        bluetoothLeScanner.startScan(leScanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        scanning = false
        bluetoothLeScanner.stopScan(leScanCallback)
    }

    @SuppressLint("MissingPermission")
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val rssi = abs(result.rssi)// Obtén el RSSI en dBm
            if (!scanResults.contains(device) && result.rssi < 100) {
                scanResults.add(device)
                val deviceName = (device.name ?: "Unknown Device").trim()
                val deviceAddress = device.address.trim()
                if (deviceName != "Unknown Device") deviceList.add("$deviceName · $deviceAddress · $rssi")
                runOnUiThread {
                    deviceListAdapter.notifyDataSetChanged()
                }
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            Toast.makeText(this@MainActivity, "ERdddddROR", Toast.LENGTH_SHORT).show()
            // No es necesario para escaneo simple, pero se puede usar para resultados por lotes
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("ScanCallback", "Error en el escaneo: $errorCode")
            Toast.makeText(this@MainActivity, "ERROR", Toast.LENGTH_SHORT).show()
        }
    }
    //endregion

    //region BLE - Gatt
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("GattCallback", "Conectado al GATT server.")
                runOnUiThread {
                    buttonConnect.visibility = View.VISIBLE
                    listView.visibility = View.GONE
                    buttonScan.visibility = View.GONE
                }
                connected = true;
                gatt?.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {

                var mainService: BluetoothGattService? =
                    gatt.services.find { it.characteristics.size == 2 }
                if (mainService != null) {
                    var characteristicsNotify: BluetoothGattCharacteristic? =
                        mainService.characteristics.find { it.descriptors.size == 1 }
                    if (characteristicsNotify != null) {

                        // Verificar propiedades de la característica
                        if ((characteristicsNotify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0)
                            return

                        gatt.readCharacteristic(characteristicsNotify)

                        // Subscribirse a notificaciones
                        gatt.setCharacteristicNotification(characteristicsNotify, true)

                        // Configurar el Client Characteristic Configuration Descriptor (CCCD)
                        val descriptor =
                            characteristicsNotify.getDescriptor(characteristicsNotify.descriptors[0].uuid)
                        descriptor.let {
                            // Leer el descriptor
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(it)
                        }
                    }
                }
            }
        }

        @Deprecated("Deprecated in Java - API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.let {
                val data: ByteArray = it.value
                showShot(byteArrayToString(data))
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun sendDataToCharacteristic(data: ByteArray) {
        if (gattDeviceDiana != null) {
            var mainService: BluetoothGattService? =
                gattDeviceDiana!!.services.find { it.characteristics.size == 2 }
            if (mainService != null) {
                var characteristicsWrite: BluetoothGattCharacteristic? =
                    mainService.characteristics.find { it.descriptors.size == 0 }
                if (characteristicsWrite != null) {

                    // Verificar propiedades de la característica
                    if ((characteristicsWrite.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0) return

                    characteristicsWrite.value = data
                    val success =
                        gattDeviceDiana?.writeCharacteristic(characteristicsWrite) ?: false
                    if (!success) {
                        Log.e("GattCallback", "Failed to write characteristic!")
                    } else {
                        Log.i("GattCallback", "Characteristic written successfully")
                    }
                }
            }
        }
    }
    //endregion

    //region ShowShot
    private fun byteArrayToString(byteArray: ByteArray): String {
        return byteArray
            .map { it.toInt().toChar() } // Convierte cada byte a Char
            .joinToString("") // Une los caracteres en un único String
    }

    fun showShot(boardValue: String) {
        val dataDart: DartData? = DataStore.dartDataList.find { it.dataBoard == boardValue }
        if (dataDart != null) {
            shotView.setText(dataDart.value)
            drawShot(dataDart.pathBoard, dataDart.pathLbl)
            turnOnLed(dataDart.value)
        }
    }

    fun drawShot(pathBoardValue: String, pathLblValue: String) {
        imageView.setImageDrawable(vectorDrawable)
        val vector: VectorChildFinder = VectorChildFinder(this, R.drawable.dartboard, imageView)

        val x = vector.findPathByName(pathBoardValue)
        if (x != null) x.setFillColor(Color.BLUE)

        val l = vector.findPathByName(pathLblValue)
        if (l != null) l.setFillColor(Color.RED)

        imageView.invalidate();
    }

    fun turnOnLed(
        shotValue: String,
        ledColor1: RGB = RGB.RED,
        ledColor2: RGB = RGB.BLUE,
        ledColor3: RGB = RGB.GREEN
    ) {
        val dataled = ByteArray(11) { 0 }

        //Color Led 1
        dataled[1] = ledColor1.red
        dataled[2] = ledColor1.green
        dataled[3] = ledColor1.blue

        val mult = shotValue[0]
        val value = shotValue.substring(1).toInt()
        //val dataLedStore =
        dataled[10] = DataStore.ledDataList[value].led

        when (mult) {
            'D' -> {
                //Animación
                dataled[0] = 0x02
                //Color Led 2
                dataled[4] = ledColor2.red
                dataled[5] = ledColor2.green
                dataled[6] = ledColor2.blue
            }

            'T' -> {
                //Animación
                dataled[0] = 0x03
                //Color Led 2
                dataled[4] = ledColor2.red
                dataled[5] = ledColor2.green
                dataled[6] = ledColor2.blue
                //Color Led 3
                dataled[7] = ledColor3.red
                dataled[8] = ledColor3.green
                dataled[9] = ledColor3.blue
            }

            else -> {
                //Animación
                dataled[0] = 0x01
            }
        }

        sendDataToCharacteristic(dataled)
    }

    //endregion
}